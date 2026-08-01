package com.branz.mmorpg.progression.evidence;

/** Durable/history inputs used to resolve one candidate without consulting wall-clock time. */
public record EvidenceContext(
        double currentEvidence,
        int identicalCompletionsLastThirtyMinutes,
        double acceptedEvidenceToday,
        boolean evidenceIdAlreadyProcessed) {
    public static final double MAXIMUM_EVIDENCE = 1000.0;

    public EvidenceContext {
        requireRange(currentEvidence, 0.0, MAXIMUM_EVIDENCE, "currentEvidence");
        if (identicalCompletionsLastThirtyMinutes < 0) {
            throw new IllegalArgumentException(
                    "identicalCompletionsLastThirtyMinutes must not be negative");
        }
        requireRange(acceptedEvidenceToday, 0.0, Double.MAX_VALUE, "acceptedEvidenceToday");
    }

    private static void requireRange(double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be finite and between " + minimum + " and " + maximum);
        }
    }
}
