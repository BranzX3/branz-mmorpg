package com.branz.mmorpg.items.definition;

import java.util.Objects;
import java.util.Optional;

public record WeaponLoadoutResolution(
        WeaponCombatProfile weapon, Optional<GuardCombatProfile> guardProfile) {
    public WeaponLoadoutResolution {
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(guardProfile, "guardProfile");
    }
}
