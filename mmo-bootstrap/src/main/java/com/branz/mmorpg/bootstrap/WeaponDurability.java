package com.branz.mmorpg.bootstrap;

record WeaponDurability(int current, int maximum) {
    WeaponDurability {
        if (maximum < 1 || current < 0 || current > maximum) {
            throw new IllegalArgumentException("invalid weapon durability");
        }
    }

    boolean broken() {
        return current == 0;
    }

    WeaponDurability spend(int amount) {
        if (amount < 1 || amount > current) {
            throw new IllegalArgumentException("weapon durability is insufficient");
        }
        return new WeaponDurability(current - amount, maximum);
    }
}
