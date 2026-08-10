package com.branz.mmorpg.combat.health;

/**
 * Deterministic health subtraction for ordinary world entities whose Bukkit health is canonical.
 */
public final class WorldHealthResolver {
    public WorldHealthResolution damage(double current, double maximum, double requestedDamage) {
        if (!Double.isFinite(current)
                || !Double.isFinite(maximum)
                || !Double.isFinite(requestedDamage)
                || maximum <= 0
                || current < 0
                || current > maximum
                || requestedDamage < 0) {
            throw new IllegalArgumentException("invalid world health damage request");
        }
        double applied = Math.min(current, requestedDamage);
        double next = Math.max(0, current - applied);
        return new WorldHealthResolution(current, next, maximum, applied, next <= 0);
    }
}
