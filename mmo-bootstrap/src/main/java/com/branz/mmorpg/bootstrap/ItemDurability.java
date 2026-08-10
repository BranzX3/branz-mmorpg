package com.branz.mmorpg.bootstrap;

/** Shared authoritative current/max durability state for durable gameplay items. */
record ItemDurability(int current, int maximum) {
    ItemDurability {
        if (maximum < 1 || current < 0 || current > maximum) {
            throw new IllegalArgumentException("invalid item durability");
        }
    }

    boolean broken() {
        return current == 0;
    }

    ItemDurability spend(int amount) {
        if (amount < 1 || amount > current) {
            throw new IllegalArgumentException("item durability is insufficient");
        }
        return new ItemDurability(current - amount, maximum);
    }
}
