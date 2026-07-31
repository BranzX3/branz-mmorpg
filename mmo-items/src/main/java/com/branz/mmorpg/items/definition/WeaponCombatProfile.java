package com.branz.mmorpg.items.definition;

import java.util.Objects;

public record WeaponCombatProfile(String family, double power) {
    public WeaponCombatProfile {
        Objects.requireNonNull(family, "family");
        if (family.isBlank()) {
            throw new IllegalArgumentException("weapon family must not be blank");
        }
        if (!Double.isFinite(power) || power <= 0) {
            throw new IllegalArgumentException("weapon power must be positive");
        }
    }
}
