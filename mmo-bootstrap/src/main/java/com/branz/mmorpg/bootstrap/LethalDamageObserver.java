package com.branz.mmorpg.bootstrap;

import org.bukkit.entity.Player;

@FunctionalInterface
interface LethalDamageObserver {
    LethalDamageObserver NONE = player -> LethalDamageDisposition.DEATH;

    LethalDamageDisposition observe(Player player);
}
