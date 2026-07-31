package com.branz.mmorpg.combat.projectile;

public enum ProjectileStatus {
    FLYING,
    IMPACTED,
    BLOCKED,
    EXPIRED;

    public boolean terminal() {
        return this != FLYING;
    }
}
