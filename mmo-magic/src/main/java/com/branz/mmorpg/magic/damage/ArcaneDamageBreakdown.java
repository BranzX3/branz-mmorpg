package com.branz.mmorpg.magic.damage;

public record ArcaneDamageBreakdown(
        double rawDamage,
        double resistanceMultiplier,
        double advantageMultiplier,
        double profileMultiplier,
        double finalDamage) {
    public ArcaneDamageBreakdown {
        if (!Double.isFinite(rawDamage)
                || rawDamage < 0
                || !Double.isFinite(resistanceMultiplier)
                || !Double.isFinite(advantageMultiplier)
                || !Double.isFinite(profileMultiplier)
                || !Double.isFinite(finalDamage)
                || finalDamage < 0) {
            throw new IllegalArgumentException("invalid arcane damage breakdown");
        }
    }
}
