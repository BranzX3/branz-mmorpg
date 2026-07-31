package com.branz.mmorpg.combat.projectile;

/** Authored server-side projectile physics and contact budget. */
public record ProjectileProfile(
        double baseSpeed,
        double gravityPerTick,
        double dragPerTick,
        double collisionRadius,
        int lifetimeTicks,
        int pierceCount) {
    public ProjectileProfile {
        if (!Double.isFinite(baseSpeed)
                || baseSpeed <= 0
                || baseSpeed > 8
                || !Double.isFinite(gravityPerTick)
                || gravityPerTick < 0
                || gravityPerTick > 1
                || !Double.isFinite(dragPerTick)
                || dragPerTick <= 0
                || dragPerTick > 1
                || !Double.isFinite(collisionRadius)
                || collisionRadius <= 0
                || collisionRadius > 2
                || lifetimeTicks < 1
                || lifetimeTicks > 400
                || pierceCount < 0
                || pierceCount > 7) {
            throw new IllegalArgumentException("invalid projectile profile");
        }
    }
}
