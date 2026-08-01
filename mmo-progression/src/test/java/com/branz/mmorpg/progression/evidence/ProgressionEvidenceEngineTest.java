package com.branz.mmorpg.progression.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProgressionEvidenceEngineTest {
    private final ProgressionEvidenceEngine engine = new ProgressionEvidenceEngine();

    @Test
    void resolvesMeaningfulEvidenceFromServerAuthoredFactors() {
        EvidenceDecision decision =
                engine.evaluate(
                        candidate(
                                EvidenceTargetKind.MEANINGFUL_ENCOUNTER, EncounterOutcome.VICTORY),
                        new EvidenceContext(90.0, 0, 0.0, false));

        assertTrue(decision.accepted());
        assertEquals(18.90625, decision.awardedEvidence(), 0.00001);
        assertEquals(108.90625, decision.resultingEvidence(), 0.00001);
        assertEquals(ReadinessBand.UNFAMILIAR, decision.previousBand());
        assertEquals(ReadinessBand.DEVELOPING, decision.resultingBand());
        assertEquals(EvidenceSuppressionReason.NONE, decision.suppressionReason());
    }

    @Test
    void repeatedEvidenceAndDailyCurveDiminishWithoutHardCap() {
        EvidenceDecision fresh =
                engine.evaluate(
                        candidate(
                                EvidenceTargetKind.MEANINGFUL_ENCOUNTER, EncounterOutcome.VICTORY),
                        new EvidenceContext(400.0, 0, 0.0, false));
        EvidenceDecision repeated =
                engine.evaluate(
                        candidate(
                                EvidenceTargetKind.MEANINGFUL_ENCOUNTER, EncounterOutcome.VICTORY),
                        new EvidenceContext(400.0, 8, 300.0, false));

        assertTrue(repeated.accepted());
        assertEquals(fresh.awardedEvidence() * 0.02, repeated.awardedEvidence(), 0.00001);
        assertTrue(repeated.awardedEvidence() > 0.0);
    }

    @Test
    void dummyEvidenceStopsAtIntroductoryFamiliarity() {
        EvidenceCandidate dummy =
                candidate(EvidenceTargetKind.TRAINING_DUMMY, EncounterOutcome.VICTORY);
        EvidenceDecision finalIntroductoryPoint =
                engine.evaluate(dummy, new EvidenceContext(24.0, 0, 0.0, false));
        EvidenceDecision suppressed =
                engine.evaluate(dummy, new EvidenceContext(25.0, 0, 0.0, false));

        assertTrue(finalIntroductoryPoint.accepted());
        assertEquals(1.0, finalIntroductoryPoint.awardedEvidence());
        assertFalse(suppressed.accepted());
        assertEquals(
                EvidenceSuppressionReason.TRAINING_DUMMY_FAMILIARITY_COMPLETE,
                suppressed.suppressionReason());
    }

    @Test
    void antiDummyContextsAlwaysResolveZeroEvidence() {
        assertSuppressed(
                EvidenceTargetKind.INVULNERABLE_TARGET,
                EvidenceSuppressionReason.INVULNERABLE_TARGET);
        assertSuppressed(
                EvidenceTargetKind.SELF_CREATED_LOOP, EvidenceSuppressionReason.SELF_CREATED_LOOP);
        assertSuppressed(
                EvidenceTargetKind.ZERO_RISK_INTERACTION,
                EvidenceSuppressionReason.ZERO_RISK_INTERACTION);

        EvidenceCandidate farBelow =
                new EvidenceCandidate(
                        UUID.randomUUID(),
                        new CharacterId(UUID.randomUUID()),
                        new EncounterId(UUID.randomUUID()),
                        ProgressionTrack.mastery("greatsword"),
                        "move-set-a",
                        "content-v1",
                        EvidenceTargetKind.MEANINGFUL_ENCOUNTER,
                        EncounterOutcome.VICTORY,
                        10.0,
                        29.0,
                        100.0,
                        0.8,
                        0.8,
                        0.8);
        EvidenceDecision decision =
                engine.evaluate(farBelow, new EvidenceContext(0.0, 0, 0.0, false));
        assertEquals(EvidenceSuppressionReason.CHALLENGE_TOO_LOW, decision.suppressionReason());
        assertEquals(0.0, decision.awardedEvidence());
    }

    @Test
    void duplicateAndAbandonedEvidenceCannotAdvanceState() {
        EvidenceCandidate meaningful =
                candidate(EvidenceTargetKind.MEANINGFUL_ENCOUNTER, EncounterOutcome.VICTORY);
        EvidenceDecision duplicate =
                engine.evaluate(meaningful, new EvidenceContext(100.0, 0, 0.0, true));
        EvidenceDecision abandoned =
                engine.evaluate(
                        candidate(
                                EvidenceTargetKind.MEANINGFUL_ENCOUNTER,
                                EncounterOutcome.ABANDONED),
                        new EvidenceContext(100.0, 0, 0.0, false));

        assertEquals(EvidenceSuppressionReason.DUPLICATE_EVIDENCE, duplicate.suppressionReason());
        assertEquals(
                EvidenceSuppressionReason.OUTCOME_NOT_MEANINGFUL, abandoned.suppressionReason());
    }

    @Test
    void readinessUsesHiddenBoundedEvidenceAndStableTrackIds() {
        assertEquals(ReadinessBand.UNFAMILIAR, ReadinessBand.fromEvidence(99.999));
        assertEquals(ReadinessBand.DEVELOPING, ReadinessBand.fromEvidence(100.0));
        assertEquals(ReadinessBand.RELIABLE, ReadinessBand.fromEvidence(300.0));
        assertEquals(ReadinessBand.REFINED, ReadinessBand.fromEvidence(600.0));
        assertEquals(ReadinessBand.EXCEPTIONAL, ReadinessBand.fromEvidence(850.0));
        assertEquals(
                "conditioning.composure",
                ProgressionTrack.conditioning(BodyConditioningAxis.COMPOSURE).id().value());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProgressionTrack(
                                com.branz.mmorpg.api.identity.DefinitionId.of("mastery.staff"),
                                ProgressionTrackType.BODY_CONDITIONING));
    }

    private void assertSuppressed(
            EvidenceTargetKind targetKind, EvidenceSuppressionReason expectedReason) {
        EvidenceDecision decision =
                engine.evaluate(
                        candidate(targetKind, EncounterOutcome.VICTORY),
                        new EvidenceContext(0.0, 0, 0.0, false));
        assertFalse(decision.accepted());
        assertEquals(0.0, decision.awardedEvidence());
        assertEquals(expectedReason, decision.suppressionReason());
    }

    private static EvidenceCandidate candidate(
            EvidenceTargetKind targetKind, EncounterOutcome outcome) {
        return new EvidenceCandidate(
                UUID.randomUUID(),
                new CharacterId(UUID.randomUUID()),
                new EncounterId(UUID.randomUUID()),
                ProgressionTrack.mastery("greatsword"),
                "move-set-a",
                "content-v1",
                targetKind,
                outcome,
                10.0,
                100.0,
                100.0,
                0.8,
                0.8,
                0.8);
    }
}
