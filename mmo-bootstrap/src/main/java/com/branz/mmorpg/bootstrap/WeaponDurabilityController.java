package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.move.MoveDefinition;
import com.branz.mmorpg.combat.move.MoveEngine;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.items.definition.WeaponCombatProfile;
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

/** Bridges committed successful combat actions to authoritative weapon durability wear. */
final class WeaponDurabilityController implements SuccessfulCombatActionObserver {
    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private final ItemEngine items;
    private final MoveEngine moves;
    private final WeaponDurabilityService durability;
    private final PvpCombatPolicy pvp;
    private final String contentVersion;
    private final Map<UUID, ArrayDeque<PendingWear>> queuedByPlayer = new HashMap<>();
    private final Map<UUID, Set<UUID>> queuedActionIds = new HashMap<>();
    private final Set<UUID> processingPlayers = new HashSet<>();
    private final Set<UUID> retryScheduled = new HashSet<>();

    WeaponDurabilityController(
            JavaPlugin plugin,
            CharacterSessionController characters,
            ItemEngine items,
            MoveEngine moves,
            WeaponDurabilityService durability,
            PvpCombatPolicy pvp,
            String contentVersion) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.items = Objects.requireNonNull(items, "items");
        this.moves = Objects.requireNonNull(moves, "moves");
        this.durability = Objects.requireNonNull(durability, "durability");
        this.pvp = Objects.requireNonNull(pvp, "pvp");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
    }

    @Override
    public void observe(CharacterId actorId, UUID actionId, DefinitionId moveId, long currentTick) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(moveId, "moveId");
        Player player = plugin.getServer().getPlayer(actorId.value());
        if (player == null || !player.isOnline() || suppressesDurability(player)) {
            return;
        }
        PendingWear pending = resolvePendingWear(player, actionId, moveId);
        if (pending == null) {
            return;
        }
        Set<UUID> actionIds =
                queuedActionIds.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());
        if (!actionIds.add(actionId)) {
            return;
        }
        queuedByPlayer
                .computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>())
                .addLast(pending);
        drain(player);
    }

    private PendingWear resolvePendingWear(Player player, UUID actionId, DefinitionId moveId) {
        MoveDefinition move = moves.find(moveId).orElse(null);
        LoadedCharacterSession session = characters.active(player).orElse(null);
        ItemId itemId = characters.equippedMainHandItemId(player).orElse(null);
        if (move == null || session == null || itemId == null) {
            return null;
        }
        ItemLocationRecord record =
                session.snapshot().itemRecords().stream()
                        .filter(candidate -> candidate.itemId().equals(itemId))
                        .findFirst()
                        .orElse(null);
        ItemDefinition definition =
                record == null ? null : items.find(record.definitionId()).orElse(null);
        WeaponCombatProfile weapon =
                definition == null ? null : definition.weaponProfile().orElse(null);
        if (definition == null
                || weapon == null
                || definition.baseMaxDurability().isEmpty()
                || !weapon.family().equals(move.family())) {
            return null;
        }
        return new PendingWear(
                actionId,
                moveId,
                itemId,
                definition.id(),
                definition.baseMaxDurability().getAsInt(),
                weapon.durabilityCostPerSuccessfulAttack());
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
                                    durability.commitSuccessfulAttack(
                                            session,
                                            pending.weaponItemId(),
                                            pending.weaponDefinitionId(),
                                            pending.baseMaximumDurability(),
                                            pending.durabilityCost(),
                                            pending.moveId(),
                                            pending.actionId(),
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
                                                                                .getPlayer(playerId);
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
        Set<UUID> actions = queuedActionIds.get(playerId);
        if (completed != null && actions != null) {
            actions.remove(completed.actionId());
        }
        clearEmpty(playerId);
    }

    private void clearEmpty(UUID playerId) {
        ArrayDeque<PendingWear> queue = queuedByPlayer.get(playerId);
        if (queue != null && queue.isEmpty()) {
            queuedByPlayer.remove(playerId);
        }
        Set<UUID> actions = queuedActionIds.get(playerId);
        if (actions != null && actions.isEmpty()) {
            queuedActionIds.remove(playerId);
        }
    }

    private void clearPlayer(UUID playerId) {
        queuedByPlayer.remove(playerId);
        queuedActionIds.remove(playerId);
        processingPlayers.remove(playerId);
        retryScheduled.remove(playerId);
    }

    private record PendingWear(
            UUID actionId,
            DefinitionId moveId,
            ItemId weaponItemId,
            DefinitionId weaponDefinitionId,
            int baseMaximumDurability,
            int durabilityCost) {}
}
