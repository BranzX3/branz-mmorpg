package com.branz.mmorpg.api.combat;

import java.util.Objects;
import java.util.UUID;

/**
 * A position, without a Bukkit Location.
 *
 * <p>Immutable and platform-independent, so combat can be tested and reasoned
 * about without a running server, and so an event payload can never carry a
 * mutable world object across a thread boundary.
 */
public record WorldPoint(UUID worldId, double x, double y, double z) {

    public WorldPoint {
        Objects.requireNonNull(worldId, "worldId");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite");
        }
    }

    public boolean sameWorld(WorldPoint other) {
        return other != null && worldId.equals(other.worldId);
    }

    /**
     * Squared distance, or {@link Double#POSITIVE_INFINITY} across worlds.
     *
     * <p>Squared so range checks avoid a square root on the hot path.
     */
    public double distanceSquared(WorldPoint other) {
        if (!sameWorld(other)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean within(WorldPoint other, double range) {
        return distanceSquared(other) <= range * range;
    }
}
