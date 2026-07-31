package com.branz.mmorpg.bootstrap;

record CatalystDurability(int current, int maximum) {
    CatalystDurability {
        if (maximum < 1 || current < 0 || current > maximum) {
            throw new IllegalArgumentException("invalid catalyst durability");
        }
    }

    CatalystDurability spend(int amount) {
        if (amount < 1 || amount > current) {
            throw new IllegalArgumentException("catalyst durability is insufficient");
        }
        return new CatalystDurability(current - amount, maximum);
    }
}
