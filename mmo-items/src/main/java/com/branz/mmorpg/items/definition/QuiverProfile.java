package com.branz.mmorpg.items.definition;

import java.util.Objects;
import java.util.Set;

public record QuiverProfile(
        int capacity,
        Set<AmmoFamily> supportedAmmoFamilies,
        int preparedAmmoCategoryCount,
        int ammoSwitchHandlingTicks) {
    public QuiverProfile {
        supportedAmmoFamilies =
                Set.copyOf(Objects.requireNonNull(supportedAmmoFamilies, "supportedAmmoFamilies"));
        if (capacity < 1
                || capacity > 4096
                || supportedAmmoFamilies.isEmpty()
                || preparedAmmoCategoryCount < 1
                || preparedAmmoCategoryCount > 4
                || ammoSwitchHandlingTicks < 0
                || ammoSwitchHandlingTicks > 40) {
            throw new IllegalArgumentException("invalid Quiver profile");
        }
    }

    public boolean supports(AmmoProfile ammo) {
        return supportedAmmoFamilies.contains(Objects.requireNonNull(ammo, "ammo").family());
    }
}
