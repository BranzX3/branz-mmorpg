package com.branz.mmorpg.items.definition;

/** Content-owned Bow timing and charge tuning compiled by Item Engine. */
public record BowWeaponProfile(
        int minimumDrawTicks,
        int fullDrawTicks,
        int freeFullDrawHoldTicks,
        double strainStaminaPerSecond,
        double minimumVelocityMultiplier,
        double minimumPostureMultiplier,
        double maximumPenetrationPercentage) {
    public BowWeaponProfile {
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
            throw new IllegalArgumentException("invalid bow weapon profile");
        }
    }

    private static boolean unitOpen(double value) {
        return Double.isFinite(value) && value > 0 && value <= 1;
    }
}
