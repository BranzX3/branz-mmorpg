package com.branz.mmorpg.combat.bow;

/** Data-driven Bow charge timing and full-draw benefits. */
public record BowDrawProfile(
        int minimumDrawTicks,
        int fullDrawTicks,
        int freeFullDrawHoldTicks,
        double strainStaminaPerSecond,
        double minimumVelocityMultiplier,
        double minimumPostureMultiplier,
        double maximumPenetrationPercentage) {
    public BowDrawProfile {
        if (minimumDrawTicks < 1
                || fullDrawTicks <= minimumDrawTicks
                || fullDrawTicks > 100
                || freeFullDrawHoldTicks < 0
                || freeFullDrawHoldTicks > 200
                || !Double.isFinite(strainStaminaPerSecond)
                || strainStaminaPerSecond <= 0
                || strainStaminaPerSecond > 20
                || !unitOpen(minimumVelocityMultiplier)
                || !unitOpen(minimumPostureMultiplier)
                || !Double.isFinite(maximumPenetrationPercentage)
                || maximumPenetrationPercentage < 0
                || maximumPenetrationPercentage > 0.40) {
            throw new IllegalArgumentException("invalid bow draw profile");
        }
    }

    private static boolean unitOpen(double value) {
        return Double.isFinite(value) && value > 0 && value <= 1;
    }
}
