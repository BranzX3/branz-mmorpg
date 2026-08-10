package com.branz.mmorpg.bootstrap;

import java.util.UUID;
import org.bukkit.entity.Player;

@FunctionalInterface
interface ShieldBlockedImpactObserver {
    ShieldBlockedImpactObserver NONE = (player, impactId) -> {};

    void observe(Player player, UUID impactId);
}
