package com.branz.mmorpg.progression.renown;

import java.util.Objects;

/**
 * Deterministic, non-decaying recognition resolver with per-fingerprint daily diminishing returns.
 */
public final class RenownEngine {
    public RenownDecision evaluate(RenownDeedCandidate candidate, RenownContext context) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(context, "context");
        if (context.duplicateDeedId()) {
            return suppressed(context, RenownSuppressionReason.DUPLICATE_DEED);
        }
        double repetitionFactor = repetitionFactor(context.identicalDeedsToday());
        if (repetitionFactor == 0) {
            return suppressed(context, RenownSuppressionReason.DAILY_REPETITION_EXHAUSTED);
        }
        int award = Math.max(1, (int) Math.floor(candidate.baseRenown() * repetitionFactor));
        return new RenownDecision(
                true,
                award,
                Math.addExact(context.currentRenown(), award),
                repetitionFactor,
                RenownSuppressionReason.NONE);
    }

    private static double repetitionFactor(int identicalDeedsToday) {
        return switch (identicalDeedsToday) {
            case 0 -> 1.0;
            case 1 -> 0.5;
            case 2 -> 0.25;
            default -> 0.0;
        };
    }

    private static RenownDecision suppressed(
            RenownContext context, RenownSuppressionReason reason) {
        return new RenownDecision(false, 0, context.currentRenown(), 0, reason);
    }
}
