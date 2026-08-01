package com.branz.mmorpg.worldloop.death;

import java.util.Objects;

/** Provider-resolved nearest valid pouch position; no map-marker semantics are implied. */
public record DeathPouchLocation(String worldKey, double x, double y, double z) {
    public DeathPouchLocation {
        Objects.requireNonNull(worldKey, "worldKey");
        if (worldKey.isBlank()) {
            throw new IllegalArgumentException("worldKey must not be blank");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("death pouch coordinates must be finite");
        }
    }
}
