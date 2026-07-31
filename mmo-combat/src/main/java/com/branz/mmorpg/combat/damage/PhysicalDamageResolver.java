package com.branz.mmorpg.combat.damage;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Canonical deterministic physical damage formula with no random hit or critical roll. */
public final class PhysicalDamageResolver {
    public static final double ARMOR_MITIGATION_CAP = 0.70;
    public static final double PENETRATION_PERCENT_CAP = 0.60;
    public static final double RESISTANCE_MINIMUM = -0.30;
    public static final double RESISTANCE_MAXIMUM = 0.60;
    public static final double ADVANTAGE_CAP = 1.60;

    public PhysicalDamageBreakdown resolve(PhysicalDamageRequest request) {
        Objects.requireNonNull(request, "request");
        double raw =
                request.weaponPower() * request.moveCoefficient() + request.flatTechniquePower();
        double penetration = Math.min(PENETRATION_PERCENT_CAP, request.penetrationPercent());
        double effectiveArmor =
                Math.max(0, request.armor() * (1 - penetration) - request.flatPenetration());
        double mitigation =
                Math.min(ARMOR_MITIGATION_CAP, effectiveArmor / (effectiveArmor + 100.0));
        double resistance =
                Math.max(
                        RESISTANCE_MINIMUM,
                        Math.min(RESISTANCE_MAXIMUM, request.physicalResistance()));
        double resistanceMultiplier = 1 - resistance;
        double advantage = advantageMultiplier(request.advantages());
        double result =
                raw
                        * (1 - mitigation)
                        * resistanceMultiplier
                        * advantage
                        * request.profileMultiplier();
        return new PhysicalDamageBreakdown(
                raw,
                effectiveArmor,
                mitigation,
                resistanceMultiplier,
                advantage,
                request.profileMultiplier(),
                result);
    }

    private static double advantageMultiplier(Set<ConditionalAdvantage> advantages) {
        List<Double> ordered =
                advantages.stream()
                        .map(ConditionalAdvantage::multiplier)
                        .sorted(Comparator.reverseOrder())
                        .toList();
        if (ordered.isEmpty()) {
            return 1;
        }
        double multiplier = ordered.getFirst();
        if (ordered.size() > 1) {
            multiplier += (ordered.get(1) - 1) * 0.5;
        }
        return Math.min(ADVANTAGE_CAP, multiplier);
    }
}
