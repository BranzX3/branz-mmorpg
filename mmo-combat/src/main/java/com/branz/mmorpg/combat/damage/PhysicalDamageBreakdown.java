package com.branz.mmorpg.combat.damage;

public record PhysicalDamageBreakdown(
        double rawDamage,
        double effectiveArmor,
        double armorMitigation,
        double resistanceMultiplier,
        double advantageMultiplier,
        double profileMultiplier,
        double finalDamage) {
    public PhysicalDamageBreakdown {
        if (!Double.isFinite(rawDamage)
                || rawDamage < 0
                || !Double.isFinite(effectiveArmor)
                || effectiveArmor < 0
                || !Double.isFinite(armorMitigation)
                || armorMitigation < 0
                || armorMitigation > 0.70
                || !Double.isFinite(resistanceMultiplier)
                || !Double.isFinite(advantageMultiplier)
                || !Double.isFinite(profileMultiplier)
                || !Double.isFinite(finalDamage)
                || finalDamage < 0) {
            throw new IllegalArgumentException("invalid physical damage breakdown");
        }
    }
}
