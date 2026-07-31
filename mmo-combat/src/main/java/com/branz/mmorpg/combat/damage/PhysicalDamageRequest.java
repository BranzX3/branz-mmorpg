package com.branz.mmorpg.combat.damage;

import java.util.Objects;
import java.util.Set;

public record PhysicalDamageRequest(
        double weaponPower,
        double moveCoefficient,
        double flatTechniquePower,
        double armor,
        double penetrationPercent,
        double flatPenetration,
        double physicalResistance,
        Set<ConditionalAdvantage> advantages,
        double profileMultiplier) {
    public PhysicalDamageRequest {
        advantages = Set.copyOf(Objects.requireNonNull(advantages, "advantages"));
        if (!finiteNonNegative(weaponPower)
                || !finiteNonNegative(flatTechniquePower)
                || !finiteNonNegative(armor)
                || !finiteNonNegative(penetrationPercent)
                || !finiteNonNegative(flatPenetration)
                || !Double.isFinite(moveCoefficient)
                || moveCoefficient <= 0
                || !Double.isFinite(physicalResistance)
                || !Double.isFinite(profileMultiplier)
                || profileMultiplier <= 0) {
            throw new IllegalArgumentException("invalid physical damage request");
        }
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0;
    }
}
