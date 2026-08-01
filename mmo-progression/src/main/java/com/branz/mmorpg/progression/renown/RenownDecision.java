package com.branz.mmorpg.progression.renown;

import java.util.Objects;

public record RenownDecision(
        boolean accepted,
        int awardedRenown,
        long resultingRenown,
        double repetitionFactor,
        RenownSuppressionReason suppressionReason) {
    public RenownDecision {
        Objects.requireNonNull(suppressionReason, "suppressionReason");
        if (awardedRenown < 0 || resultingRenown < 0 || repetitionFactor < 0) {
            throw new IllegalArgumentException("renown decision values must not be negative");
        }
        if (accepted != (suppressionReason == RenownSuppressionReason.NONE)) {
            throw new IllegalArgumentException(
                    "accepted decision must use NONE suppression reason");
        }
        if (!accepted && (awardedRenown != 0 || repetitionFactor != 0)) {
            throw new IllegalArgumentException("suppressed decision cannot award renown");
        }
    }
}
