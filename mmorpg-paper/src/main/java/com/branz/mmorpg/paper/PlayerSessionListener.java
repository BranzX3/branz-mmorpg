package com.branz.mmorpg.paper;

import com.branz.mmorpg.core.player.PlayerSessionService;
import com.branz.mmorpg.core.stat.PlayerAttributeService;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bridges Paper's join and quit events to the session lifecycle.
 *
 * <p>Only the UUID and name cross this boundary. No {@code Player} object is
 * captured by the session layer, so nothing can hold a reference to a logged-out
 * player.
 *
 * <p>A failed load is reported but does not kick: the player stays connected
 * with MMO features disabled, which is easier for an operator to diagnose than a
 * disconnect loop, and is what the fail-closed rule requires — gameplay
 * mutation is refused rather than performed against an empty profile.
 */
public final class PlayerSessionListener implements Listener {

    private final JavaPlugin plugin;
    private final PlayerSessionService sessions;
    private final PlayerAttributeService attributes;

    public PlayerSessionListener(JavaPlugin plugin, PlayerSessionService sessions,
                                 PlayerAttributeService attributes) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.attributes = Objects.requireNonNull(attributes, "attributes");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        sessions.login(playerId, name).whenComplete((session, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE,
                        "MMO session load failed for " + name + " (" + playerId
                                + "); MMO features stay disabled for this player", failure);
            } else if (session.profile().classId().isPresent()) {
                attributes.activate(playerId);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        attributes.forget(playerId);
        sessions.logout(playerId).whenComplete((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE,
                        "MMO session save failed for " + playerId
                                + "; the profile is retained as a pending save", failure);
            }
        });
    }
}
