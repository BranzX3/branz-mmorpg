package com.branz.mmorpg.progression.evidence;

import java.util.Objects;

/** Deterministic result ready for durable batching and qualitative feedback. */
public record EvidenceDecision(
        boolean accepted,
        double awardedEvidence,
        double resultingEvidence,
        ReadinessBand previousBand,
        ReadinessBand resultingBand,
        EvidenceSuppressionReason suppressionReason,
        EvidenceFactorBreakdown factors) {
    public EvidenceDecision {
        Objects.requireNonNull(previousBand, "previousBand");
        Objects.requireNonNull(resultingBand, "resultingBand");
        Objects.requireNonNull(suppressionReason, "suppressionReason");
        Objects.requireNonNull(factors, "factors");
        if (accepted != (suppressionReason == EvidenceSuppressionReason.NONE)) {
            throw new IllegalArgumentException(
                    "accepted decisions must use NONE and suppressed decisions must provide a reason");
        }
    }
}
