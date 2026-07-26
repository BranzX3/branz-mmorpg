package com.branz.mmorpg.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.mob.MobAbilityDefinition;
import com.branz.mmorpg.api.mob.MobAiState;
import com.branz.mmorpg.api.mob.MobDecision;
import com.branz.mmorpg.api.mob.MobDefinition;
import com.branz.mmorpg.api.mob.MobRuntimeSnapshot;
import com.branz.mmorpg.api.mob.MobTargetCandidate;
import com.branz.mmorpg.api.mob.SpatialPosition;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MobAiEngineTest {
    private static final UUID WORLD = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MOB = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-26T00:00:00Z");
    private final MobAiEngine engine = new MobAiEngine();

    @Test
    void decisionAndPathCadencesAreBounded() {
        MobRuntimeSnapshot mob = spawned();
        MobTargetCandidate far = target(TARGET, 10, 1);
        MobDecision first = engine.decide(definition(), mob, List.of(far), NOW);
        assertEquals(MobDecision.Action.ACQUIRE, first.action());
        assertTrue(first.requestPath());

        MobDecision presentationTick = engine.decide(
                definition(), first.snapshot(), List.of(far), NOW.plusMillis(10));
        assertEquals(MobDecision.Action.NONE, presentationTick.action());
        assertFalse(presentationTick.requestPath());

        MobDecision nextDecision = engine.decide(
                definition(), first.snapshot(), List.of(far), NOW.plusMillis(250));
        assertEquals(MobDecision.Action.MOVE, nextDecision.action());
        assertFalse(nextDecision.requestPath());
    }

    @Test
    void targetHysteresisAvoidsSmallThreatFlapsButAllowsMaterialSwitch() {
        MobRuntimeSnapshot acquired = engine.decide(
                definition(), spawned(), List.of(target(TARGET, 10, 1)), NOW).snapshot();
        UUID challenger = UUID.fromString("30000000-0000-0000-0000-000000000002");
        MobDecision stable = engine.decide(definition(), acquired,
                List.of(target(TARGET, 10, 1), target(challenger, 9, 1.05)),
                NOW.plusMillis(250));
        assertEquals(TARGET, stable.targetId().orElseThrow());

        MobDecision switched = engine.decide(definition(), stable.snapshot(),
                List.of(target(TARGET, 10, 1), target(challenger, 9, 3)),
                NOW.plusMillis(500));
        assertEquals(challenger, switched.targetId().orElseThrow());
    }

    @Test
    void leashResetReturnsToCanonicalHomeAndFullHealth() {
        MobRuntimeSnapshot hurt = engine.withHealth(spawned(), 40);
        MobRuntimeSnapshot away = engine.withPosition(
                hurt, new SpatialPosition(WORLD, 50, 64, 0));
        MobDecision reset = engine.decide(definition(), away, List.of(), NOW);
        assertEquals(MobAiState.RESET, reset.snapshot().state());
        assertTrue(reset.requestPath());

        MobDecision canonical = engine.decide(
                definition(), reset.snapshot(), List.of(), NOW.plusMillis(1000));
        assertEquals(MobAiState.IDLE, canonical.snapshot().state());
        assertEquals(canonical.snapshot().home(), canonical.snapshot().position());
        assertEquals(100, canonical.snapshot().health());
    }

    @Test
    void deathTransitionsOnceAndAllocatesOneRewardSequence() {
        MobRuntimeSnapshot zero = engine.withHealth(spawned(), 0);
        MobDecision death = engine.decide(definition(), zero, List.of(), NOW);
        assertEquals(MobDecision.Action.DIE, death.action());
        assertEquals(1, death.snapshot().rewardSequence());
        MobDecision replay = engine.decide(
                definition(), death.snapshot(), List.of(), NOW.plusSeconds(1));
        assertEquals(MobDecision.Action.NONE, replay.action());
        assertEquals(1, replay.snapshot().rewardSequence());
    }

    @Test
    void abilitySelectionHonorsRangeAndHealthConditions() {
        MobRuntimeSnapshot hurt = engine.withHealth(spawned(), 40);
        MobDecision result = engine.decide(
                definition(), hurt, List.of(target(TARGET, 3, 1)), NOW);
        assertEquals(MobDecision.Action.CAST, result.action());
        assertEquals(ContentId.parse("branz:test_skill"), result.skillId().orElseThrow());
    }

    private static MobRuntimeSnapshot spawned() {
        return MobRuntimeSnapshot.spawn(
                MOB, ContentId.parse("branz:test_mob"), 1,
                new SpatialPosition(WORLD, 0, 64, 0), 100, NOW);
    }

    private static MobTargetCandidate target(UUID id, double x, double threat) {
        return new MobTargetCandidate(id, new SpatialPosition(WORLD, x, 64, 0),
                true, true, threat, Set.of());
    }

    private static MobDefinition definition() {
        return new MobDefinition(
                ContentId.parse("branz:test_mob"), "Test Mob", Map.of("max_health", 100d),
                new MobDefinition.Scaling(0, 0, 1), "hostile",
                MobDefinition.TargetPolicy.HOSTILE_PLAYERS,
                new MobDefinition.Navigation(0.25, 250, 500, false),
                List.of(new MobAbilityDefinition(ContentId.parse("branz:test_skill"),
                        1, 0, 4, 0.5, Set.of())),
                20, 30, 750, Optional.empty(), Set.of(), Map.of(),
                ContentId.parse("branz:test_loot"), 1,
                new MobDefinition.Presentation("minecraft:zombie", Optional.empty()));
    }
}
