package com.branz.mmorpg.combat.guard;

/** Training weapon-guard defaults derived from the V1 defense contract. */
public record GuardProfile(
        double coneDegrees,
        double physicalBlockRatio,
        int perfectWindowTicks,
        double maximumStability,
        int recoveryDelayTicks,
        double inactiveRecoveryPerSecond,
        double activeRecoveryPerSecond,
        int breakTicks,
        double stabilityAfterBreak) {
    public GuardProfile {
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
            throw new IllegalArgumentException("invalid guard profile");
        }
    }

    public static GuardProfile trainingWeapon() {
        return new GuardProfile(120, 0.80, 4, 100, 30, 20, 8, 24, 35);
    }
}
