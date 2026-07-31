package com.branz.mmorpg.magic.damage;

import com.branz.mmorpg.combat.damage.ConditionalAdvantage;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Deterministic arcane channel formula; armor never mitigates this component. */
public final class ArcaneDamageResolver {
    public static final double RESISTANCE_MINIMUM = -0.30;
    public static final double RESISTANCE_MAXIMUM = 0.60;
    public static final double ADVANTAGE_CAP = 1.60;

    public ArcaneDamageBreakdown resolve(ArcaneDamageRequest request) {
        Objects.requireNonNull(request, "request");
        double raw = request.catalystPower() * request.powerCoefficient();
        double resistance =
                Math.max(RESISTANCE_MINIMUM, Math.min(RESISTANCE_MAXIMUM, request.resistance()));
        double resistanceMultiplier = 1 - resistance;
        double advantage = advantageMultiplier(request);
        return new ArcaneDamageBreakdown(
                raw,
                resistanceMultiplier,
                advantage,
                request.profileMultiplier(),
                raw * resistanceMultiplier * advantage * request.profileMultiplier());
    }

    private static double advantageMultiplier(ArcaneDamageRequest request) {
        List<Double> ordered =
                request.advantages().stream()
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
