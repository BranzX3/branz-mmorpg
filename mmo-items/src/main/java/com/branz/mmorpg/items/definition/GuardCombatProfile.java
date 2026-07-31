package com.branz.mmorpg.items.definition;

/** Item-owned deterministic guard tuning compiled without coupling Item Engine to Combat. */
public record GuardCombatProfile(
        double coneDegrees,
        double physicalBlockRatio,
        int perfectWindowTicks,
        double maximumStability,
        int recoveryDelayTicks,
        double inactiveRecoveryPerSecond,
        double activeRecoveryPerSecond,
        int breakTicks,
        double stabilityAfterBreak) {
    public GuardCombatProfile {
        if (!Double.isFinite(coneDegrees)
                || coneDegrees <= 0
                || coneDegrees > 360
                || !Double.isFinite(physicalBlockRatio)
                || physicalBlockRatio < 0
                || physicalBlockRatio > 1
                || perfectWindowTicks < 1
                || !Double.isFinite(maximumStability)
                || maximumStability <= 0
                || recoveryDelayTicks < 0
                || !Double.isFinite(inactiveRecoveryPerSecond)
                || inactiveRecoveryPerSecond < 0
                || !Double.isFinite(activeRecoveryPerSecond)
                || activeRecoveryPerSecond < 0
                || breakTicks < 1
                || !Double.isFinite(stabilityAfterBreak)
                || stabilityAfterBreak < 0
                || stabilityAfterBreak > maximumStability) {
            throw new IllegalArgumentException("invalid guard combat profile");
        }
    }
}
