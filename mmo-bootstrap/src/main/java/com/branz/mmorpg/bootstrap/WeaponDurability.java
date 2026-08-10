package com.branz.mmorpg.bootstrap;

record WeaponDurability(int current, int maximum) {
    WeaponDurability {
        new ItemDurability(current, maximum);
    }

    boolean broken() {
        return current == 0;
    }

    WeaponDurability spend(int amount) {
        ItemDurability next = new ItemDurability(current, maximum).spend(amount);
        return new WeaponDurability(next.current(), next.maximum());
    }
}
