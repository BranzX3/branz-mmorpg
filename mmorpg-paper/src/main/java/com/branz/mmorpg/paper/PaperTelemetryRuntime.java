package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.runtime.Scheduler;
import com.branz.mmorpg.api.telemetry.TelemetryService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public final class PaperTelemetryRuntime implements Listener {
    private final TelemetryService telemetry;
    private final Scheduler scheduler;

    public PaperTelemetryRuntime(TelemetryService telemetry, Scheduler scheduler) {
        this.telemetry = java.util.Objects.requireNonNull(telemetry, "telemetry");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        telemetry.increment("combat.hit");
        telemetry.observe("combat.damage.max", event.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        telemetry.increment(event.getEntity() instanceof Player
                ? "combat.player_death" : "combat.mob_death");
    }

    public void poll() {
        int depth = scheduler.queueDepth();
        if (depth >= 0) telemetry.observe("scheduler.queue_depth.max", depth);
    }
}
