package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.combat.DamageRequest;
import com.branz.mmorpg.api.combat.DamageType;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.stat.ModifierSource;
import com.branz.mmorpg.api.stat.AttributeModifier;
import com.branz.mmorpg.api.stat.AttributeType;
import com.branz.mmorpg.api.status.StatusApplication;
import com.branz.mmorpg.api.status.StatusDefinition;
import com.branz.mmorpg.api.status.StatusInstance;
import com.branz.mmorpg.core.runtime.SystemGameClock;
import com.branz.mmorpg.core.status.BuiltInStatuses;
import com.branz.mmorpg.core.status.StatusWheel;
import com.branz.mmorpg.core.stat.PlayerAttributeService;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Applies Core status definitions to live Paper entities through one central wheel. */
public final class PaperStatusRuntime implements Listener {

    private final JavaPlugin plugin;
    private final PaperCombatRuntime combat;
    private final PlayerAttributeService playerAttributes;
    private final ContentService content;
    private final StatusWheel wheel;
    private final SystemGameClock clock = new SystemGameClock();
    private final Map<UUID, java.util.Set<String>> appliedModifierIds = new HashMap<>();

    public PaperStatusRuntime(JavaPlugin plugin, PaperCombatRuntime combat,
                              PlayerAttributeService playerAttributes, ContentService content) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.playerAttributes = Objects.requireNonNull(playerAttributes, "playerAttributes");
        this.content = Objects.requireNonNull(content, "content");
        this.wheel = new StatusWheel(id -> this.content.snapshot().statuses().get(id));
    }

    public StatusApplication apply(UUID targetId, ContentId statusId, UUID sourceId) {
        StatusDefinition definition = content.snapshot().statuses().get(statusId);
        if (definition == null) {
            throw new IllegalArgumentException("unknown status " + statusId);
        }
        double ccResistance = playerAttributes.find(targetId)
                .map(ignored -> playerAttributes.attributes(targetId)
                        .get(AttributeType.CROWD_CONTROL_RESISTANCE))
                .orElse(0.0);
        StatusApplication result = wheel.container(targetId).apply(definition,
                ModifierSource.of(ModifierSource.SourceType.SKILL, sourceId.toString()),
                null, ccResistance, clock.now());
        if (result.applied()) {
            syncModifiers(targetId);
        }
        if (result.outcome() == StatusApplication.Outcome.APPLIED
                && statusId.equals(BuiltInStatuses.SHIELD)) {
            Entity target = plugin.getServer().getEntity(targetId);
            if (target instanceof LivingEntity living) {
                living.setAbsorptionAmount(Math.min(2048.0,
                        living.getAbsorptionAmount() + definition.potency()));
            }
        }
        return result;
    }

    public int remove(UUID targetId, ContentId statusId) {
        int removed = wheel.removeDefinition(targetId, statusId);
        if (removed > 0) syncModifiers(targetId);
        return removed;
    }

    public boolean has(UUID targetId, ContentId statusId) {
        return wheel.has(targetId, statusId);
    }

    public java.util.List<StatusInstance> active(UUID targetId) {
        return wheel.active(targetId);
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
                syncModifiers(targetId);
            }
        });
        // Also covers reconnects whose asynchronous Player Session activation
        // completed after PlayerJoinEvent.
        for (UUID targetId : List.copyOf(appliedModifierIds.keySet())) {
            syncModifiers(targetId);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        wheel.disconnect(event.getPlayer().getUniqueId(), clock.now());
        syncModifiers(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        wheel.reconnect(event.getPlayer().getUniqueId(), clock.now());
        // Session activation can complete after this event. The retained ID set
        // is reconciled by the central tick once the stat block becomes active.
        appliedModifierIds.computeIfAbsent(event.getPlayer().getUniqueId(), ignored -> new HashSet<>());
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        UUID targetId = event.getEntity().getUniqueId();
        wheel.unregister(targetId);
        syncModifiers(targetId);
        appliedModifierIds.remove(targetId);
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

    private void syncModifiers(UUID targetId) {
        if (playerAttributes.find(targetId).isEmpty()) return;
        Map<String, AttributeModifier> desired = new HashMap<>();
        for (StatusInstance instance : wheel.active(targetId)) {
            StatusDefinition definition = content.snapshot().statuses().get(instance.definitionId());
            if (definition == null) continue;
            ModifierSource source = ModifierSource.of(
                    ModifierSource.SourceType.STATUS, instance.modifierPrefix());
            for (AttributeModifier template : definition.modifiers()) {
                String id = instance.modifierPrefix() + ":" + template.id();
                desired.put(id, new AttributeModifier(id, template.attribute(), template.operation(),
                        template.value() * instance.stacks(), source, template.stackingGroup(),
                        template.priority(), java.util.Optional.ofNullable(instance.expiresAt())));
            }
        }

        java.util.Set<String> previous = appliedModifierIds
                .computeIfAbsent(targetId, ignored -> new HashSet<>());
        for (String oldId : new HashSet<>(previous)) {
            if (!desired.containsKey(oldId)) playerAttributes.removeModifier(targetId, oldId);
        }
        desired.values().forEach(modifier -> playerAttributes.addModifier(targetId, modifier));
        previous.clear();
        previous.addAll(desired.keySet());
    }
}
