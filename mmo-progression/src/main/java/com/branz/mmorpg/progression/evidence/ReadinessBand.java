package com.branz.mmorpg.progression.evidence;

/** Player-facing qualitative band. Exact evidence remains hidden. */
public enum ReadinessBand {
    UNFAMILIAR,
    DEVELOPING,
    RELIABLE,
    REFINED,
    EXCEPTIONAL;

    public static ReadinessBand fromEvidence(double evidence) {
        if (!Double.isFinite(evidence)
                || evidence < 0.0
                || evidence > EvidenceContext.MAXIMUM_EVIDENCE) {
            throw new IllegalArgumentException("evidence must be between 0 and 1000");
        }
        if (evidence < 100.0) {
            return UNFAMILIAR;
        }
        if (evidence < 300.0) {
            return DEVELOPING;
        }
        if (evidence < 600.0) {
            return RELIABLE;
        }
        if (evidence < 850.0) {
            return REFINED;
        }
        return EXCEPTIONAL;
    }
}
