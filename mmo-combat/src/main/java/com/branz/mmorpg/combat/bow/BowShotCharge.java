package com.branz.mmorpg.combat.bow;

public record BowShotCharge(
        double drawRatio,
        double velocityMultiplier,
        double postureMultiplier,
        double penetrationPercentage) {
    public BowShotCharge {
        if (!unit(drawRatio)
                || !unit(velocityMultiplier)
                || !unit(postureMultiplier)
                || !Double.isFinite(penetrationPercentage)
                || penetrationPercentage < 0
                || penetrationPercentage > 0.40) {
            throw new IllegalArgumentException("invalid bow shot charge");
        }
    }

    private static boolean unit(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }
}
