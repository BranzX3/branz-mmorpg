package com.branz.mmorpg.items.definition;

import java.util.Objects;

public record AmmoProfile(AmmoFamily family) {
    public AmmoProfile {
        Objects.requireNonNull(family, "family");
    }
}
