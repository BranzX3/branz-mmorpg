package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Bridges blocked PvE impacts to authoritative shield durability wear. */
final class ShieldDurabilityController implements BlockedImpactObserver {
    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private final ItemEngineLookup items;
    private final ShieldDurabilityService durability;
    private final PvpCombatPolicy pvp;
    private final String contentVersion;
    private final Consumer<Player> durabilityChanged;
    private final Map<UUID, ArrayDeque<PendingWear>> queuedByPlayer = new HashMap<>();
    private final Set<UUID> processingPlayers = new HashSet<>();
    private final Set<UUID> retryScheduled = new HashSet<>();

    ShieldDurabilityController(
            JavaPlugin plugin,
            CharacterSessionController characters,
            ItemEngineLookup items,
            ShieldDurabilityService durability,
            PvpCombatPolicy pvp,
            String contentVersion,
            Consumer<Player> durabilityChanged) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.items = Objects.requireNonNull(items, "items");
        this.durability = Objects.requireNonNull(durability, "durability");
        this.pvp = Objects.requireNonNull(pvp, "pvp");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        this.durabilityChanged = Objects.requireNonNull(durabilityChanged, "durabilityChanged");
    }

    @Override
    public void observe(Player player) {
        Objects.requireNonNull(player, "player");
        if (!player.isOnline() || suppressesDurability(player)) {
            return;
        }
        PendingWear pending = resolvePendingWear(player);
        if (pending == null) {
            return;
        }
        queuedByPlayer
                .computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>())
                .addLast(pending);
        drain(player);
    }

    private PendingWear resolvePendingWear(Player player) {
        LoadedCharacterSession session = characters.active(player).orElse(null);
        ItemId itemId =
                session == null
                        ? null
                        : session.snapshot().equipment().item(
                                        com.branz.mmorpg.items.equipment.EquipmentSlot.OFF_HAND)
                                .orElse(null);
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
                UUID.randomUUID(),
                itemId,
                definition.id(),
                definition.baseMaxDurability().getAsInt());
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
                                            pending.operationId(),
                                            contentVersion);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    characters.completeExternalValueMutation(
                                                            session,
                                                            result,
                                                            completed ->
                                                                    completeCommit(
                                                                            playerId,
                                                                            completed)));
                        });
    }

    private void completeCommit(
            UUID playerId, Result<LoadedCharacterSession, CharacterSessionErrorCode> result) {
        processingPlayers.remove(playerId);
        completeHead(playerId);
        Player current = plugin.getServer().getPlayer(playerId);
        if (current == null || !current.isOnline()) {
            clearPlayer(playerId);
            return;
        }
        if (result instanceof Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>) {
            durabilityChanged.accept(current);
        } else if (result
                instanceof Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode> failure) {
            current.sendActionBar(
                    Component.text(
                            "Shield durability commit failed: " + failure.error().code(),
                            NamedTextColor.RED));
        }
        drain(current);
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
        queue.pollFirst();
        clearEmpty(playerId);
    }

    private void clearEmpty(UUID playerId) {
        ArrayDeque<PendingWear> queue = queuedByPlayer.get(playerId);
        if (queue != null && queue.isEmpty()) {
            queuedByPlayer.remove(playerId);
        }
    }

    private void clearPlayer(UUID playerId) {
        queuedByPlayer.remove(playerId);
        processingPlayers.remove(playerId);
        retryScheduled.remove(playerId);
    }

    private record PendingWear(
            UUID operationId,
            ItemId shieldItemId,
            DefinitionId shieldDefinitionId,
            int baseMaximumDurability) {}

    @FunctionalInterface
    interface ItemEngineLookup {
        java.util.Optional<ItemDefinition> find(DefinitionId definitionId);
    }
}
