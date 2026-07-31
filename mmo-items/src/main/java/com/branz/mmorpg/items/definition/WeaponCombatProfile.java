package com.branz.mmorpg.items.definition;

import java.util.Objects;
import java.util.Optional;

public record WeaponCombatProfile(
        String family,
        double power,
        Optional<BowWeaponProfile> bowProfile,
        Optional<CrossbowWeaponProfile> crossbowProfile,
        OffhandPolicy offhandPolicy,
        Optional<GuardCombatProfile> guardProfile) {
    public WeaponCombatProfile {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(bowProfile, "bowProfile");
        Objects.requireNonNull(crossbowProfile, "crossbowProfile");
        Objects.requireNonNull(offhandPolicy, "offhandPolicy");
        Objects.requireNonNull(guardProfile, "guardProfile");
        if (family.isBlank()) {
            throw new IllegalArgumentException("weapon family must not be blank");
        }
        if (!Double.isFinite(power) || power <= 0) {
            throw new IllegalArgumentException("weapon power must be positive");
        }
        if (family.equals("BOW") != bowProfile.isPresent()) {
            throw new IllegalArgumentException("only BOW family requires bow profile");
        }
        if (family.equals("CROSSBOW") != crossbowProfile.isPresent()) {
            throw new IllegalArgumentException("only CROSSBOW family requires crossbow profile");
        }
        if (bowProfile.isPresent() && crossbowProfile.isPresent()) {
            throw new IllegalArgumentException("weapon handling profiles are mutually exclusive");
        }
        if (family.equals("GREATSWORD")
                && (offhandPolicy != OffhandPolicy.EMPTY || guardProfile.isEmpty())) {
            throw new IllegalArgumentException(
                    "GREATSWORD requires EMPTY off-hand policy and weapon guard profile");
        }
        if (family.equals("SWORD_SHIELD") && offhandPolicy != OffhandPolicy.SHIELD) {
            throw new IllegalArgumentException("SWORD_SHIELD requires SHIELD off-hand policy");
        }
    }

    public WeaponCombatProfile(
            String family,
            double power,
            Optional<BowWeaponProfile> bowProfile,
            Optional<CrossbowWeaponProfile> crossbowProfile) {
        this(family, power, bowProfile, crossbowProfile, OffhandPolicy.ANY, Optional.empty());
    }

    public WeaponCombatProfile(String family, double power, Optional<BowWeaponProfile> bowProfile) {
        this(family, power, bowProfile, Optional.empty());
    }

    public WeaponCombatProfile(String family, double power) {
        this(
                family,
                power,
                Optional.empty(),
                Optional.empty(),
                OffhandPolicy.ANY,
                Optional.empty());
    }
}
