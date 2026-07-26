package com.branz.mmorpg.core.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.stat.ModifierSource;
import com.branz.mmorpg.api.status.StatusDefinition;
import com.branz.mmorpg.api.status.StatusInstance;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StatusWheelTest {

    private static final ModifierSource CASTER =
            ModifierSource.of(ModifierSource.SourceType.SKILL, "caster");

    private final Map<ContentId, StatusDefinition> catalog = BuiltInStatuses.all();

    @Test
    void oneSweepAdvancesEveryTarget() {
        FixedGameClock clock = FixedGameClock.at("2026-07-25T12:00:00Z");
        StatusWheel wheel = new StatusWheel(catalog::get);
        List<UUID> targets = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID target = UUID.randomUUID();
            targets.add(target);
            wheel.container(target).apply(catalog.get(BuiltInStatuses.BURN), CASTER, null, 0.0, clock.now());
        }
        RecordingHandler handler = new RecordingHandler();

        clock.advance(Duration.ofSeconds(2));
        assertEquals(3, wheel.advance(clock, handler));

        assertEquals(3, handler.ticks.get());
        assertEquals(3, wheel.trackedTargets());
        assertEquals(3, wheel.activeEffects());
    }

    @Test
    void tenThousandEffectsAdvanceInOneSweepWithoutSchedulingAnything() {
        FixedGameClock clock = FixedGameClock.at("2026-07-25T12:00:00Z");
        StatusWheel wheel = new StatusWheel(catalog::get);
        for (int i = 0; i < 10_000; i++) {
            wheel.container(UUID.randomUUID())
                    .apply(catalog.get(BuiltInStatuses.POISON), CASTER, null, 0.0, clock.now());
        }
        RecordingHandler handler = new RecordingHandler();
        int threadsBefore = Thread.activeCount();

        clock.advance(Duration.ofSeconds(3));
        long start = System.nanoTime();
        int touched = wheel.advance(clock, handler);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertEquals(10_000, touched);
        assertEquals(10_000, handler.ticks.get());
        assertTrue(Thread.activeCount() <= threadsBefore + 1,
                "the wheel must not create a task per effect");
        assertTrue(elapsedMillis < 1_000,
                "one sweep over 10k effects should be far under a second, took " + elapsedMillis + "ms");
    }

    @Test
    void expiryIsReportedOnceAndTheInstanceIsGone() {
        FixedGameClock clock = FixedGameClock.at("2026-07-25T12:00:00Z");
        StatusWheel wheel = new StatusWheel(catalog::get);
        UUID target = UUID.randomUUID();
        wheel.container(target).apply(catalog.get(BuiltInStatuses.STUN), CASTER, null, 0.0, clock.now());
        RecordingHandler handler = new RecordingHandler();

        clock.advance(Duration.ofSeconds(2));
        wheel.advance(clock, handler);
        wheel.advance(clock, handler);

        assertEquals(1, handler.expiries.size(), "an expiry fires exactly once");
        assertEquals(0, wheel.activeEffects());
    }

    @Test
    void aStatusWhoseDefinitionLeftTheSnapshotIsDroppedNotTicked() {
        FixedGameClock clock = FixedGameClock.at("2026-07-25T12:00:00Z");
        Map<ContentId, StatusDefinition> mutable = new java.util.HashMap<>(catalog);
        StatusWheel wheel = new StatusWheel(mutable::get);
        UUID target = UUID.randomUUID();
        wheel.container(target).apply(catalog.get(BuiltInStatuses.BURN), CASTER, null, 0.0, clock.now());
        RecordingHandler handler = new RecordingHandler();

        mutable.remove(BuiltInStatuses.BURN);
        clock.advance(Duration.ofSeconds(2));
        wheel.advance(clock, handler);

        assertEquals(0, handler.ticks.get(), "an undescribable effect must not tick");
        assertEquals(0, wheel.activeEffects());
    }

    @Test
    void unregisterDropsEverythingForATarget() {
        FixedGameClock clock = FixedGameClock.at("2026-07-25T12:00:00Z");
        StatusWheel wheel = new StatusWheel(catalog::get);
        UUID target = UUID.randomUUID();
        wheel.container(target).apply(catalog.get(BuiltInStatuses.BURN), CASTER, null, 0.0, clock.now());
        wheel.container(target).apply(catalog.get(BuiltInStatuses.SHIELD), CASTER, null, 0.0, clock.now());

        assertEquals(2, wheel.unregister(target).size());

        assertFalse(wheel.isRegistered(target), "nothing survives logout");
        assertEquals(0, wheel.trackedTargets());
        assertEquals(0, wheel.activeEffects());
    }

    @Test
    void readOnlyQueriesDoNotRegisterEmptyTargets() {
        StatusWheel wheel = new StatusWheel(catalog::get);
        UUID unknown = UUID.randomUUID();

        assertFalse(wheel.has(unknown, BuiltInStatuses.BURN));
        assertTrue(wheel.active(unknown).isEmpty());
        assertEquals(0, wheel.removeDefinition(unknown, BuiltInStatuses.BURN));
        assertEquals(0, wheel.trackedTargets());
    }

    @Test
    void disconnectHonorsPauseTickDownAndClearPolicies() {
        FixedGameClock clock = FixedGameClock.at("2026-07-25T12:00:00Z");
        Map<ContentId, StatusDefinition> definitions = new java.util.HashMap<>(catalog);
        StatusDefinition clear = new StatusDefinition(ContentId.parse("branz:logout_clear"), "Clear",
                com.branz.mmorpg.api.status.StatusCategory.NEUTRAL,
                com.branz.mmorpg.api.status.StackPolicy.UNIQUE, 1, Duration.ofSeconds(30),
                Duration.ZERO, 0.0, List.of(), java.util.Set.of(),
                com.branz.mmorpg.api.status.CrowdControlCategory.NONE,
                com.branz.mmorpg.api.status.OfflinePolicy.CLEAR);
        definitions.put(clear.id(), clear);
        StatusWheel wheel = new StatusWheel(definitions::get);
        UUID target = UUID.randomUUID();
        wheel.container(target).apply(catalog.get(BuiltInStatuses.SHIELD), CASTER, null, 0.0, clock.now());
        wheel.container(target).apply(catalog.get(BuiltInStatuses.BURN), CASTER, null, 0.0, clock.now());
        wheel.container(target).apply(clear, CASTER, null, 0.0, clock.now());

        wheel.disconnect(target, clock.now());
        clock.advance(Duration.ofSeconds(7));
        assertEquals(1, wheel.reconnect(target, clock.now()),
                "shield pauses, burn expires while offline, CLEAR is discarded");
        assertTrue(wheel.container(target).has(BuiltInStatuses.SHIELD));
        assertEquals(10_000L,
                wheel.container(target).active().get(0).remainingMillis(clock.now()));
    }

    private static final class RecordingHandler implements StatusWheel.TickHandler {

        private final AtomicInteger ticks = new AtomicInteger();
        private final List<StatusInstance> expiries = new ArrayList<>();

        @Override
        public void onTick(UUID targetId, StatusInstance instance, StatusDefinition definition) {
            ticks.incrementAndGet();
        }

        @Override
        public void onExpire(UUID targetId, StatusInstance instance, StatusDefinition definition) {
            expiries.add(instance);
        }
    }
}
