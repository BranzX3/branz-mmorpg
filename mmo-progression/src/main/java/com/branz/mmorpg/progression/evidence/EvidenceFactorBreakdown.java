package com.branz.mmorpg.progression.evidence;

/** Exact internal factors recorded for deterministic diagnostics, never normal player UI. */
public record EvidenceFactorBreakdown(
        double challenge,
        double outcome,
        double diversity,
        double execution,
        double novelty,
        double repetition,
        double risk,
        double dailyCurve) {
    public static EvidenceFactorBreakdown zero() {
        return new EvidenceFactorBreakdown(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
