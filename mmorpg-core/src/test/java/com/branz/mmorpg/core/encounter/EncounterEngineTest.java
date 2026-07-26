package com.branz.mmorpg.core.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.encounter.ContributionType;
import com.branz.mmorpg.api.encounter.EncounterDefinition;
import com.branz.mmorpg.api.encounter.EncounterSnapshot;
import com.branz.mmorpg.api.encounter.EncounterState;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EncounterEngineTest {
    private static final UUID INSTANCE = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-26T00:00:00Z");
    private final EncounterEngine engine = new EncounterEngine();

    @Test
    void referenceBossProgressesThroughThreePhasesAndStableCompletion() {
        EncounterSnapshot active = active();
        assertEquals(0, active.phaseIndex());
        active = engine.bossHealth(active, definition(), 0.65, NOW.plusSeconds(2));
        assertEquals(1, active.phaseIndex());
        active = engine.bossHealth(active, definition(), 0.30, NOW.plusSeconds(3));
        assertEquals(2, active.phaseIndex());
        active = engine.contribute(active, PLAYER, ContributionType.DAMAGE, 100);
        EncounterSnapshot success =
                engine.bossHealth(active, definition(), 0, NOW.plusSeconds(4));
        assertEquals(EncounterState.SUCCESS, success.state());
        assertEquals("encounter:" + INSTANCE + ":1",
                success.completionId().orElseThrow());
        assertEquals(Set.of(PLAYER), engine.eligibleRewards(success, definition()));
    }

    @Test
    void wipeWaitsForGraceAndReconnectCancelsWipe() {
        EncounterSnapshot active = engine.disconnect(active(), PLAYER, NOW.plusSeconds(2));
        assertEquals(EncounterState.ACTIVE,
                engine.wipe(active, definition(), NOW.plusMillis(2500)).state());
        EncounterSnapshot reconnected =
                engine.connect(active, PLAYER, NOW.plusMillis(2600));
        assertEquals(EncounterState.ACTIVE,
                engine.wipe(reconnected, definition(), NOW.plusSeconds(10)).state());

        EncounterSnapshot empty =
                engine.disconnect(reconnected, PLAYER, NOW.plusSeconds(11));
        assertEquals(EncounterState.FAILED,
                engine.wipe(empty, definition(), NOW.plusSeconds(12)).state());
    }

    @Test
    void partySnapshotRejectsLateTaggingAndKeepsDisconnectedContribution() {
        EncounterSnapshot active = active();
        UUID late = UUID.fromString("50000000-0000-0000-0000-000000000002");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> engine.contribute(active, late, ContributionType.DAMAGE, 999));
        EncounterSnapshot contributed =
                engine.contribute(active, PLAYER, ContributionType.OBJECTIVE, 50);
        contributed = engine.disconnect(contributed, PLAYER, NOW.plusSeconds(2));
        contributed = engine.connect(contributed, PLAYER, NOW.plusSeconds(3));
        assertEquals(50, contributed.contributions().get(PLAYER)
                .get(ContributionType.OBJECTIVE));
    }

    @Test
    void rewardAndCleanupRetriesAreIdempotent() {
        EncounterSnapshot success =
                engine.bossHealth(engine.contribute(active(), PLAYER,
                        ContributionType.DAMAGE, 100), definition(), 0, NOW.plusSeconds(2));
        EncounterSnapshot rewarded = engine.markRewarded(success, PLAYER);
        assertEquals(rewarded, engine.markRewarded(rewarded, PLAYER));

        EncounterSnapshot cleaning = engine.beginCleanup(rewarded, NOW.plusSeconds(3));
        EncounterSnapshot partial =
                engine.cleanup(cleaning, Set.of(ACTOR), Set.of(), NOW.plusSeconds(4));
        assertEquals(EncounterState.CLEANING, partial.state());
        EncounterSnapshot closed =
                engine.cleanup(partial, Set.of(ACTOR), Set.of("world:0:0"), NOW.plusSeconds(5));
        assertEquals(EncounterState.CLOSED, closed.state());
        assertTrue(closed.actorIds().isEmpty());
        assertTrue(closed.forcedChunkKeys().isEmpty());
        assertEquals(closed, engine.cleanup(
                closed, Set.of(ACTOR), Set.of("world:0:0"), NOW.plusSeconds(6)));
        assertFalse(closed.completionId().isEmpty());
    }

    private EncounterSnapshot active() {
        EncounterSnapshot created =
                engine.create(INSTANCE, definition(), Set.of(PLAYER), NOW);
        EncounterSnapshot preparing = engine.prepare(created, NOW);
        return engine.activate(preparing, definition(),
                Set.of(ACTOR), Set.of("world:0:0"), NOW.plusSeconds(1));
    }

    private static EncounterDefinition definition() {
        ContentId slash = ContentId.parse("branz:heavy_slash");
        ContentId burst = ContentId.parse("branz:fire_burst");
        return new EncounterDefinition(ContentId.parse("branz:seal_guardian_encounter"),
                "Seal Guardian", EncounterDefinition.Mode.PRIVATE_PARTY,
                ContentId.parse("branz:seal_guardian"),
                List.of(
                        new EncounterDefinition.Phase("warded", 0.70,
                                Set.of(slash), Set.of(), 1),
                        new EncounterDefinition.Phase("fracture", 0.35,
                                Set.of(slash, burst), Set.of(), 1.25),
                        new EncounterDefinition.Phase("unsealed", 0,
                                Set.of(burst), Set.of(), 1.75)),
                40, 1000, 750, 300_000, 1, 5, 50,
                EncounterDefinition.PartyPolicy.SNAPSHOT_AT_START,
                false, ContentId.parse("branz:aether_cache"));
    }
}
