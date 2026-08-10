package com.branz.mmorpg.bootstrap;

import org.bukkit.entity.Player;

@FunctionalInterface
interface BlockedImpactObserver {
    BlockedImpactObserver NONE = player -> {};

    void observe(Player player);
}
