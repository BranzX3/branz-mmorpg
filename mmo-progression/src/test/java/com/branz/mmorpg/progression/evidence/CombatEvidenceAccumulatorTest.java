package com.branz.mmorpg.progression.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEvidenceAccumulatorTest {
    @Test
    void emitsOnceAtOutcomeAndIncludesCommittedMisses() {
        CombatEvidenceAccumulator accumulator = new CombatEvidenceAccumulator();
        UUID targetId = UUID.randomUUID();
        UUID hitOne = UUID.randomUUID();
        UUID miss = UUID.randomUUID();
        UUID hitTwo = UUID.randomUUID();
        assertTrue(observe(accumulator, targetId, hitOne, "move.greatsword.one", 0.2));
        accumulator.observeCommittedAction(
                "greatsword", miss, DefinitionId.of("move.greatsword.missed"));
        assertTrue(observe(accumulator, targetId, hitTwo, "move.greatsword.one", 0.7));

        List<EvidenceCandidate> completed =
                accumulator.completeTarget(
                        new CharacterId(UUID.randomUUID()),
                        targetId,
                        "content.test.1",
                        EncounterOutcome.VICTORY);

        assertEquals(2, completed.size());
        assertEquals(5.0, completed.getFirst().baseEvidence());
        assertEquals(1.0 / 3.0, completed.getFirst().moveDiversityRatio(), 0.00001);
        assertEquals(2.0 / 3.0, completed.getFirst().executionQuality(), 0.00001);
        assertEquals(0.7, completed.getFirst().stressRatio());
        assertEquals(0, accumulator.activeTargetCount());
        assertTrue(
                accumulator
                        .completeTarget(
                                completed.getFirst().characterId(),
                                targetId,
                                "content.test.1",
                                EncounterOutcome.VICTORY)
                        .isEmpty());
    }

    @Test
    void completesEveryTargetForDefeatAndEnforcesActiveTargetBound() {
        CombatEvidenceAccumulator accumulator = new CombatEvidenceAccumulator();
        for (int index = 0; index < CombatEvidenceAccumulator.MAXIMUM_ACTIVE_TARGETS; index++) {
            assertTrue(
                    observe(
                            accumulator,
                            new UUID(0, index + 1L),
                            UUID.randomUUID(),
                            "move.greatsword.one",
                            0.4));
        }
        assertFalse(
                observe(
                        accumulator,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "move.greatsword.one",
                        0.4));
        accumulator.observeStress(1.0);

        List<EvidenceCandidate> completed =
                accumulator.completeAll(
                        new CharacterId(UUID.randomUUID()),
                        "content.test.1",
                        EncounterOutcome.DEFEAT);

        assertEquals(CombatEvidenceAccumulator.MAXIMUM_ACTIVE_TARGETS * 2, completed.size());
        assertTrue(
                completed.stream()
                        .allMatch(candidate -> candidate.outcome() == EncounterOutcome.DEFEAT));
        assertTrue(completed.stream().allMatch(candidate -> candidate.stressRatio() == 1.0));
        assertEquals(0, accumulator.activeTargetCount());
    }

    @Test
    void reengagingTheSameLivingTargetCreatesANewEncounterIdentity() {
        CombatEvidenceAccumulator accumulator = new CombatEvidenceAccumulator();
        CharacterId characterId = new CharacterId(UUID.randomUUID());
        UUID targetId = UUID.randomUUID();
        assertTrue(observe(accumulator, targetId, UUID.randomUUID(), "move.greatsword.one", 0.4));
        List<EvidenceCandidate> retreat =
                accumulator.completeTarget(
                        characterId, targetId, "content.test.1", EncounterOutcome.RETREAT);
        assertTrue(observe(accumulator, targetId, UUID.randomUUID(), "move.greatsword.one", 0.4));
        List<EvidenceCandidate> victory =
                accumulator.completeTarget(
                        characterId, targetId, "content.test.1", EncounterOutcome.VICTORY);

        assertFalse(retreat.getFirst().encounterId().equals(victory.getFirst().encounterId()));
        assertFalse(retreat.getFirst().evidenceId().equals(victory.getFirst().evidenceId()));
    }

    private static boolean observe(
            CombatEvidenceAccumulator accumulator,
            UUID targetId,
            UUID actionId,
            String moveId,
            double stress) {
        return accumulator.observeSuccessfulAction(
                targetId,
                EvidenceTargetKind.MEANINGFUL_ENCOUNTER,
                "minecraft:zombie",
                80.0,
                "greatsword",
                BodyConditioningAxis.MIGHT,
                actionId,
                DefinitionId.of(moveId),
                100.0,
                stress);
    }
}
