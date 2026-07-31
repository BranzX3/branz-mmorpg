package com.branz.mmorpg.combat.crossbow;

/** Content-owned reload timing; both values are measured in server ticks. */
public record CrossbowReloadProfile(int boltPlacementTicks, int lockingTicks) {
    public CrossbowReloadProfile {
        if (boltPlacementTicks < 1 || lockingTicks < 1) {
            throw new IllegalArgumentException("Crossbow reload timings must be positive");
        }
    }
}
