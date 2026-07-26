package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.combat.DamageRequest;
import com.branz.mmorpg.api.combat.DamageType;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.stat.ModifierSource;
import com.branz.mmorpg.api.status.StatusApplication;
import com.branz.mmorpg.api.status.StatusDefinition;
import com.branz.mmorpg.api.status.StatusInstance;
import com.branz.mmorpg.core.runtime.SystemGameClock;
import com.branz.mmorpg.core.status.BuiltInStatuses;
import com.branz.mmorpg.core.status.StatusWheel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Applies Core status definitions to live Paper entities through one central wheel. */
public final class PaperStatusRuntime implements Listener {

    private final JavaPlugin plugin;
    private final PaperCombatRuntime combat;
    private final Map<ContentId, StatusDefinition> definitions = BuiltInStatuses.all();
    private final StatusWheel wheel = new StatusWheel(definitions::get);
    private final SystemGameClock clock = new SystemGameClock();

    public PaperStatusRuntime(JavaPlugin plugin, PaperCombatRuntime combat) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.combat = Objects.requireNonNull(combat, "combat");
    }

    public StatusApplication apply(UUID targetId, ContentId statusId, UUID sourceId) {
        StatusDefinition definition = definitions.get(statusId);
        if (definition == null) {
            throw new IllegalArgumentException("unknown status " + statusId);
        }
        StatusApplication result = wheel.container(targetId).apply(definition,
                ModifierSource.of(ModifierSource.SourceType.SKILL, sourceId.toString()),
                null, 0.0, clock.now());
        if (result.applied() && statusId.equals(BuiltInStatuses.SHIELD)) {
            Entity target = plugin.getServer().getEntity(targetId);
            if (target instanceof LivingEntity living) {
                living.setAbsorptionAmount(Math.min(2048.0,
                        living.getAbsorptionAmount() + definition.potency()));
            }
        }
        return result;
    }

    public int remove(UUID targetId, ContentId statusId) {
        return wheel.container(targetId).removeDefinition(statusId);
    }

    public boolean has(UUID targetId, ContentId statusId) {
        return wheel.container(targetId).has(statusId);
    }

    public java.util.List<StatusInstance> active(UUID targetId) {
        return wheel.container(targetId).active();
    }

    public void tick() {
        wheel.advance(clock, new StatusWheel.TickHandler() {
            @Override
            public void onTick(UUID targetId, StatusInstance instance, StatusDefinition definition) {
                Entity raw = plugin.getServer().getEntity(targetId);
                if (!(raw instanceof LivingEntity target) || target.isDead()) {
                    return;
                }
                double amount = Math.max(0.0, definition.potency() * instance.stacks());
                if (definition.id().equals(BuiltInStatuses.REGENERATION)) {
                    target.setHealth(Math.min(target.getMaxHealth(), target.getHealth() + amount));
                    return;
                }
                UUID source = parseUuid(instance.source().id());
                UUID tickId = UUID.nameUUIDFromBytes(
                        ("status:" + instance.instanceId() + ":" + clock.epochMilli())
                                .getBytes(StandardCharsets.UTF_8));
                try {
                    if (source != null && plugin.getServer().getEntity(source) != null) {
                        combat.engine().damage(new DamageRequest(tickId, source, targetId,
                                DamageType.MAGIC, amount, 0.0, false, 1));
                    } else {
                        combat.engine().damage(DamageRequest.environmental(tickId, targetId, amount));
                    }
                } finally {
                    combat.engine().endCast(tickId);
                }
            }

            @Override
            public void onExpire(UUID targetId, StatusInstance instance, StatusDefinition definition) {
                // Attribute/status presentation cleanup is stateless for the
                // current vanilla adapter.
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        wheel.disconnect(event.getPlayer().getUniqueId(), clock.now());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        wheel.reconnect(event.getPlayer().getUniqueId(), clock.now());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!has(playerId, BuiltInStatuses.ROOT) && !has(playerId, BuiltInStatuses.STUN)) {
            return;
        }
        if (event.hasChangedPosition()) {
            event.setCancelled(true);
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
