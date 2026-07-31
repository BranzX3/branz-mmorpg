package com.branz.mmorpg.items.definition;

/** Content-owned timing for the two persistent Crossbow reload checkpoints. */
public record CrossbowWeaponProfile(int boltPlacementTicks, int lockingTicks) {
    public CrossbowWeaponProfile {
        if (boltPlacementTicks < 1 || lockingTicks < 1) {
            throw new IllegalArgumentException("invalid crossbow weapon profile");
        }
    }
}
