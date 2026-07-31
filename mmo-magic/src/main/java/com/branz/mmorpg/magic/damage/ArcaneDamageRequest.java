package com.branz.mmorpg.magic.damage;

import com.branz.mmorpg.combat.damage.ConditionalAdvantage;
import com.branz.mmorpg.magic.definition.ArcaneSchool;
import java.util.Objects;
import java.util.Set;

public record ArcaneDamageRequest(
        ArcaneSchool school,
        double catalystPower,
        double powerCoefficient,
        double resistance,
        Set<ConditionalAdvantage> advantages,
        double profileMultiplier) {
    public ArcaneDamageRequest {
        Objects.requireNonNull(school, "school");
        advantages = Set.copyOf(Objects.requireNonNull(advantages, "advantages"));
        if (!Double.isFinite(catalystPower)
                || catalystPower < 0
                || !Double.isFinite(powerCoefficient)
                || powerCoefficient <= 0
                || !Double.isFinite(resistance)
                || !Double.isFinite(profileMultiplier)
                || profileMultiplier <= 0) {
            throw new IllegalArgumentException("invalid arcane damage request");
        }
    }
}
