package com.branz.mmorpg.progression.evidence;

import java.util.Objects;

/** V1 authoritative Mastery and Body Conditioning evidence resolver. */
public final class ProgressionEvidenceEngine {
    static final double TRAINING_DUMMY_FAMILIARITY_LIMIT = 25.0;
    private static final double MAXIMUM_AWARD_PER_CANDIDATE = 100.0;

    public EvidenceDecision evaluate(EvidenceCandidate candidate, EvidenceContext context) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(context, "context");
        ReadinessBand previousBand = ReadinessBand.fromEvidence(context.currentEvidence());
        EvidenceSuppressionReason rejection = suppressionReason(candidate, context);
        if (rejection != EvidenceSuppressionReason.NONE) {
            return suppressed(context.currentEvidence(), previousBand, rejection);
        }

        EvidenceFactorBreakdown factors = factors(candidate, context);
        double rawAward =
                candidate.baseEvidence()
                        * factors.challenge()
                        * factors.outcome()
                        * factors.diversity()
                        * factors.execution()
                        * factors.novelty()
                        * factors.repetition()
                        * factors.risk()
                        * factors.dailyCurve();
        double award = Math.min(MAXIMUM_AWARD_PER_CANDIDATE, rawAward);
        if (candidate.targetKind() == EvidenceTargetKind.TRAINING_DUMMY) {
            award = Math.min(award, TRAINING_DUMMY_FAMILIARITY_LIMIT - context.currentEvidence());
        }
        award = Math.min(award, EvidenceContext.MAXIMUM_EVIDENCE - context.currentEvidence());
        if (award <= 0.0) {
            return suppressed(
                    context.currentEvidence(),
                    previousBand,
                    EvidenceSuppressionReason.OUTCOME_NOT_MEANINGFUL);
        }
        double resultingEvidence = context.currentEvidence() + award;
        return new EvidenceDecision(
                true,
                award,
                resultingEvidence,
                previousBand,
                ReadinessBand.fromEvidence(resultingEvidence),
                EvidenceSuppressionReason.NONE,
                factors);
    }

    private static EvidenceSuppressionReason suppressionReason(
            EvidenceCandidate candidate, EvidenceContext context) {
        if (context.evidenceIdAlreadyProcessed()) {
            return EvidenceSuppressionReason.DUPLICATE_EVIDENCE;
        }
        if (context.currentEvidence() >= EvidenceContext.MAXIMUM_EVIDENCE) {
            return EvidenceSuppressionReason.MAXIMUM_REACHED;
        }
        if (candidate.outcome() == EncounterOutcome.ABANDONED || candidate.baseEvidence() == 0.0) {
            return EvidenceSuppressionReason.OUTCOME_NOT_MEANINGFUL;
        }
        switch (candidate.targetKind()) {
            case INVULNERABLE_TARGET -> {
                return EvidenceSuppressionReason.INVULNERABLE_TARGET;
            }
            case SELF_CREATED_LOOP -> {
                return EvidenceSuppressionReason.SELF_CREATED_LOOP;
            }
            case ZERO_RISK_INTERACTION -> {
                return EvidenceSuppressionReason.ZERO_RISK_INTERACTION;
            }
            case TRAINING_DUMMY -> {
                if (context.currentEvidence() >= TRAINING_DUMMY_FAMILIARITY_LIMIT) {
                    return EvidenceSuppressionReason.TRAINING_DUMMY_FAMILIARITY_COMPLETE;
                }
            }
            case MEANINGFUL_ENCOUNTER -> {
                // Continue to the challenge gate below.
            }
            default ->
                    throw new IllegalStateException(
                            "Unsupported evidence target kind " + candidate.targetKind());
        }
        if (candidate.challengeRating() / candidate.demonstratedCapability() < 0.30) {
            return EvidenceSuppressionReason.CHALLENGE_TOO_LOW;
        }
        return EvidenceSuppressionReason.NONE;
    }

    private static EvidenceFactorBreakdown factors(
            EvidenceCandidate candidate, EvidenceContext context) {
        return new EvidenceFactorBreakdown(
                challengeFactor(candidate.challengeRating() / candidate.demonstratedCapability()),
                candidate.outcome().evidenceFactor(),
                0.50 + 0.75 * candidate.moveDiversityRatio(),
                0.50 + 0.75 * candidate.executionQuality(),
                context.identicalCompletionsLastThirtyMinutes() == 0 ? 1.25 : 1.0,
                repetitionFactor(context.identicalCompletionsLastThirtyMinutes()),
                riskFactor(candidate.stressRatio(), candidate.outcome()),
                dailyFactor(context.acceptedEvidenceToday()));
    }

    private static double challengeFactor(double ratio) {
        if (ratio < 0.60) {
            return 0.50;
        }
        if (ratio < 0.90) {
            return 0.85;
        }
        if (ratio < 1.15) {
            return 1.0;
        }
        if (ratio < 1.50) {
            return 1.25;
        }
        return 1.50;
    }

    private static double repetitionFactor(int repetitions) {
        return switch (repetitions) {
            case 0 -> 1.0;
            case 1 -> 0.75;
            case 2 -> 0.50;
            case 3 -> 0.25;
            default -> 0.10;
        };
    }

    private static double riskFactor(double stressRatio, EncounterOutcome outcome) {
        if (stressRatio < 0.30) {
            return 0.25;
        }
        if (stressRatio < 0.75) {
            return 1.0;
        }
        if (stressRatio <= 0.95) {
            return 1.25;
        }
        return outcome == EncounterOutcome.VICTORY ? 0.75 : 0.25;
    }

    private static double dailyFactor(double acceptedEvidenceToday) {
        if (acceptedEvidenceToday < 100.0) {
            return 1.0;
        }
        if (acceptedEvidenceToday < 250.0) {
            return 0.50;
        }
        return 0.25;
    }

    private static EvidenceDecision suppressed(
            double evidence, ReadinessBand band, EvidenceSuppressionReason suppressionReason) {
        return new EvidenceDecision(
                false,
                0.0,
                evidence,
                band,
                band,
                suppressionReason,
                EvidenceFactorBreakdown.zero());
    }
}
