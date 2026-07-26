package com.branz.mmorpg.api.combat;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable contribution accumulated by one attacker against one victim. */
public record CombatContribution(
        UUID contributorId,
        double healthDamage,
        double shieldDamage,
        double threat,
        int hitCount,
        Instant lastContributionAt) {

    public CombatContribution {
        Objects.requireNonNull(contributorId, "contributorId");
        Objects.requireNonNull(lastContributionAt, "lastContributionAt");
        if (!validAmount(healthDamage) || !validAmount(shieldDamage)
                || !validAmount(threat) || hitCount < 1) {
            throw new IllegalArgumentException("invalid combat contribution");
        }
    }

    public double effectiveDamage() {
        return healthDamage + shieldDamage;
    }

    private static boolean validAmount(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }
}
