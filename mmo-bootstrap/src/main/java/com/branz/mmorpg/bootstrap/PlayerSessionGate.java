package com.branz.mmorpg.bootstrap;

import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

final class PlayerSessionGate implements Listener {
    private static final Component MAINTENANCE_MESSAGE =
            Component.text("Branz MMO is loading or in safe maintenance mode.");

    private final BootstrapLifecycle lifecycle;

    PlayerSessionGate(BootstrapLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerConnectionValidateLoginEvent event) {
        if (!lifecycle.acceptsSessions()) {
            event.kickMessage(MAINTENANCE_MESSAGE);
        }
    }
}
