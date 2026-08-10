package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Bridges resolved blocked impacts to authoritative off-hand Shield durability wear. */
final class ShieldDurabilityController implements ShieldBlockedImpactObserver {
    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private final ItemEngine items;
    private final ShieldDurabilityService durability;
    private final PvpCombatPolicy pvp;
    private final String contentVersion;
    private final Map<UUID, ArrayDeque<PendingWear>> queuedByPlayer = new HashMap<>();
    private final Map<UUID, Set<UUID>> queuedImpactIds = new HashMap<>();
    private final Set<UUID> processingPlayers = new HashSet<>();
    private final Set<UUID> retryScheduled = new HashSet<>();

    ShieldDurabilityController(
            JavaPlugin plugin,
            CharacterSessionController characters,
            ItemEngine items,
            ShieldDurabilityService durability,
            PvpCombatPolicy pvp,
            String contentVersion) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.items = Objects.requireNonNull(items, "items");
        this.durability = Objects.requireNonNull(durability, "durability");
        this.pvp = Objects.requireNonNull(pvp, "pvp");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
    }

    @Override
    public void observe(Player player, UUID impactId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(impactId, "impactId");
        if (!player.isOnline() || suppressesDurability(player)) {
            return;
        }
        PendingWear pending = resolvePendingWear(player, impactId);
        if (pending == null) {
            return;
        }
        Set<UUID> impactIds =
                queuedImpactIds.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());
        if (!impactIds.add(impactId)) {
            return;
        }
        queuedByPlayer
                .computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>())
                .addLast(pending);
        drain(player);
    }

    private PendingWear resolvePendingWear(Player player, UUID impactId) {
        LoadedCharacterSession session = characters.active(player).orElse(null);
        ItemId itemId =
                session == null
                        ? null
                        : session.snapshot().equipment().item(EquipmentSlot.OFF_HAND).orElse(null);
        if (session == null || itemId == null) {
            return null;
        }
        ItemLocationRecord record =
                session.snapshot().itemRecords().stream()
                        .filter(candidate -> candidate.itemId().equals(itemId))
                        .findFirst()
                        .orElse(null);
        ItemDefinition definition =
                record == null ? null : items.find(record.definitionId()).orElse(null);
        if (definition == null
                || definition.shieldProfile().isEmpty()
                || definition.baseMaxDurability().isEmpty()) {
            return null;
        }
        return new PendingWear(
                impactId, itemId, definition.id(), definition.baseMaxDurability().getAsInt());
    }

    private void drain(Player player) {
        UUID playerId = player.getUniqueId();
        if (processingPlayers.contains(playerId)) {
            return;
        }
        ArrayDeque<PendingWear> queue = queuedByPlayer.get(playerId);
        PendingWear pending = queue == null ? null : queue.peekFirst();
        if (pending == null) {
            clearEmpty(playerId);
            return;
        }
        if (!player.isOnline()) {
            clearPlayer(playerId);
            return;
        }
        if (suppressesDurability(player)) {
            completeHead(playerId);
            drain(player);
            return;
        }
        LoadedCharacterSession session = characters.beginExternalValueMutation(player).orElse(null);
        if (session == null) {
            scheduleRetry(playerId);
            return;
        }
        processingPlayers.add(playerId);
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> result =
                                    durability.commitBlockedImpact(
                                            session,
                                            pending.shieldItemId(),
                                            pending.shieldDefinitionId(),
                                            pending.baseMaximumDurability(),
                                            pending.impactId(),
                                            contentVersion);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    characters.completeExternalValueMutation(
                                                            session,
                                                            result,
                                                            completed -> {
                                                                processingPlayers.remove(playerId);
                                                                completeHead(playerId);
                                                                Player current =
                                                                        plugin.getServer()
                                                                                .getPlayer(
                                                                                        playerId);
                                                                if (current != null
                                                                        && current.isOnline()) {
                                                                    drain(current);
                                                                } else {
                                                                    clearPlayer(playerId);
                                                                }
                                                            }));
                        });
    }

    private boolean suppressesDurability(Player player) {
        return pvp.activeProfile(player)
                .map(profile -> !profile.durabilityLossAllowed())
                .orElse(false);
    }

    private void scheduleRetry(UUID playerId) {
        if (!retryScheduled.add(playerId)) {
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            retryScheduled.remove(playerId);
                            Player player = plugin.getServer().getPlayer(playerId);
                            if (player == null || !player.isOnline()) {
                                clearPlayer(playerId);
                                return;
                            }
                            drain(player);
                        },
                        2L);
    }

    private void completeHead(UUID playerId) {
        ArrayDeque<PendingWear> queue = queuedByPlayer.get(playerId);
        if (queue == null) {
            return;
        }
        PendingWear completed = queue.pollFirst();
        Set<UUID> impacts = queuedImpactIds.get(playerId);
        if (completed != null && impacts != null) {
            impacts.remove(completed.impactId());
        }
        clearEmpty(playerId);
    }

    private void clearEmpty(UUID playerId) {
        ArrayDeque<PendingWear> queue = queuedByPlayer.get(playerId);
        if (queue != null && queue.isEmpty()) {
            queuedByPlayer.remove(playerId);
        }
        Set<UUID> impacts = queuedImpactIds.get(playerId);
        if (impacts != null && impacts.isEmpty()) {
            queuedImpactIds.remove(playerId);
        }
    }

    private void clearPlayer(UUID playerId) {
        queuedByPlayer.remove(playerId);
        queuedImpactIds.remove(playerId);
        processingPlayers.remove(playerId);
        retryScheduled.remove(playerId);
    }

    private record PendingWear(
            UUID impactId,
            ItemId shieldItemId,
            DefinitionId shieldDefinitionId,
            int baseMaximumDurability) {}
}
