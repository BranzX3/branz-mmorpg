package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.runtime.Scheduler;
import com.branz.mmorpg.api.social.TradeService;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Logout cancellation and restart recovery for direct-trade escrow. */
public final class PaperSocialRuntime implements Listener {
    private final JavaPlugin plugin;
    private final TradeService trades;
    private final Scheduler scheduler;

    public PaperSocialRuntime(JavaPlugin plugin, TradeService trades, Scheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.trades = Objects.requireNonNull(trades, "trades");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        scheduler.async(trades::recover).exceptionally(failure -> {
            plugin.getLogger().warning("Trade recovery failed: " + failure.getMessage());
            return java.util.List.of();
        });
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        scheduler.async(() -> trades.logout(event.getPlayer().getUniqueId()))
                .exceptionally(failure -> {
                    plugin.getLogger().warning("Trade logout cancellation failed: "
                            + failure.getMessage());
                    return null;
                });
    }
}
