package com.branz.mmorpg.progression.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEvidenceCandidateFactoryTest {
    private final CombatEvidenceCandidateFactory factory = new CombatEvidenceCandidateFactory();

    @Test
    void composesStableMasteryAndConditioningCandidatesFromOneOutcome() {
        CombatEncounterSummary summary = summary(4, 3, 2);

        List<EvidenceCandidate> first = factory.create(summary);
        List<EvidenceCandidate> replay = factory.create(summary);

        assertEquals(first, replay);
        assertEquals(2, first.size());
        assertEquals(ProgressionTrack.mastery("greatsword"), first.getFirst().track());
        assertEquals(
                ProgressionTrack.conditioning(BodyConditioningAxis.MIGHT), first.getLast().track());
        assertNotEquals(first.getFirst().evidenceId(), first.getLast().evidenceId());
        assertEquals(7.5, first.getFirst().baseEvidence());
        assertEquals(3.75, first.getLast().baseEvidence());
        assertEquals(0.5, first.getFirst().moveDiversityRatio());
        assertEquals(0.75, first.getFirst().executionQuality());
    }

    @Test
    void capsCandidateBaseAndRejectsUnboundedOrWrongTrackSummaries() {
        CombatEncounterSummary maximum = summary(64, 64, 4);
        assertEquals(100.0, factory.create(maximum).getFirst().baseEvidence());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CombatEncounterSummary(
                                maximum.characterId(),
                                maximum.encounterId(),
                                ProgressionTrack.conditioning(BodyConditioningAxis.MIGHT),
                                maximum.conditioningTrack(),
                                maximum.noveltyFingerprint(),
                                maximum.contentVersion(),
                                maximum.targetKind(),
                                maximum.outcome(),
                                maximum.challengeRating(),
                                maximum.demonstratedCapability(),
                                maximum.committedActions(),
                                maximum.successfulActions(),
                                maximum.distinctMoves(),
                                maximum.peakStressRatio()));
        assertThrows(IllegalArgumentException.class, () -> summary(65, 1, 1));
    }

    private static CombatEncounterSummary summary(
            int committedActions, int successfulActions, int distinctMoves) {
        return new CombatEncounterSummary(
                new CharacterId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                new EncounterId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                ProgressionTrack.mastery("greatsword"),
                ProgressionTrack.conditioning(BodyConditioningAxis.MIGHT),
                "zombie:greatsword",
                "content.test.1",
                EvidenceTargetKind.MEANINGFUL_ENCOUNTER,
                EncounterOutcome.VICTORY,
                80.0,
                100.0,
                committedActions,
                successfulActions,
                distinctMoves,
                0.6);
    }
}
