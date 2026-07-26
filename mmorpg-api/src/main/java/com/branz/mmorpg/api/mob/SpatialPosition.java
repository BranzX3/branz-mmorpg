package com.branz.mmorpg.api.mob;

import java.util.Objects;
import java.util.UUID;

public record SpatialPosition(UUID worldId, double x, double y, double z) {
    public SpatialPosition {
        Objects.requireNonNull(worldId, "worldId");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("position must be finite");
        }
    }

    public double distanceSquared(SpatialPosition other) {
        if (!worldId.equals(other.worldId)) return Double.POSITIVE_INFINITY;
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
