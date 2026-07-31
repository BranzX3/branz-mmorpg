package com.branz.mmorpg.items.definition;

import java.util.Objects;
import java.util.Optional;

public record WeaponCombatProfile(
        String family, double power, Optional<BowWeaponProfile> bowProfile) {
    public WeaponCombatProfile {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(bowProfile, "bowProfile");
        if (family.isBlank()) {
            throw new IllegalArgumentException("weapon family must not be blank");
        }
        if (!Double.isFinite(power) || power <= 0) {
            throw new IllegalArgumentException("weapon power must be positive");
        }
        if (family.equals("BOW") != bowProfile.isPresent()) {
            throw new IllegalArgumentException("only BOW family requires bow profile");
        }
    }

    public WeaponCombatProfile(String family, double power) {
        this(family, power, Optional.empty());
    }
}
