package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.crossbow.CrossbowPersistentState;
import com.branz.mmorpg.combat.resource.FlaskAllocation;
import com.branz.mmorpg.items.consumable.ConsumableDefinitionProfile;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.items.definition.QuiverProfile;
import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.items.quiver.QuiverPreparation;
import com.branz.mmorpg.persistence.progression.KnowledgeAcquisitionRequest;
import com.branz.mmorpg.persistence.progression.TeachingCommitRequest;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.progression.build.CharacterBuild;
import com.branz.mmorpg.progression.evidence.EvidenceCandidate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Acquires the character lease, loads DB truth, then unlocks the Paper player projection. */
final class CharacterSessionController implements Listener {
    private final JavaPlugin plugin;
    private final CharacterSessionService sessions;
    private final BukkitInventoryProjectionService projections;
    private final ItemEngine itemEngine;
    private final long heartbeatTicks;
    private final Map<UUID, UUID> loadAttempts = new HashMap<>();
    private final Map<UUID, LoadedCharacterSession> active = new HashMap<>();
    private final Set<UUID> packReady = new HashSet<>();
    private final Set<UUID> projected = new HashSet<>();
    private final Set<UUID> valueMutationInFlight = new HashSet<>();
    private final List<Consumer<Player>> readyHandlers = new ArrayList<>();
    private int heartbeatTaskId = -1;

    CharacterSessionController(
            JavaPlugin plugin,
            CharacterSessionService sessions,
            BukkitInventoryProjectionService projections,
            ItemEngine itemEngine,
            DatabaseSettings databaseSettings) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.projections = Objects.requireNonNull(projections, "projections");
        this.itemEngine = Objects.requireNonNull(itemEngine, "itemEngine");
        Objects.requireNonNull(databaseSettings, "databaseSettings");
        heartbeatTicks = Math.max(1L, databaseSettings.leaseHeartbeat().toMillis() / 50L);
    }

    void start() {
        heartbeatTaskId =
                plugin.getServer()
                        .getScheduler()
                        .scheduleSyncRepeatingTask(
                                plugin, this::heartbeatAll, heartbeatTicks, heartbeatTicks);
        plugin.getServer().getOnlinePlayers().forEach(this::beginLoad);
    }

    void addReadyHandler(Consumer<Player> readyHandler) {
        readyHandlers.add(Objects.requireNonNull(readyHandler, "readyHandler"));
    }

    void onPackReady(Player player) {
        packReady.add(player.getUniqueId());
        applyProjectionIfReady(player);
    }

    boolean ready(Player player) {
        return projected.contains(Objects.requireNonNull(player, "player").getUniqueId());
    }

    boolean valueMutationInFlight(Player player) {
        return valueMutationInFlight.contains(
                Objects.requireNonNull(player, "player").getUniqueId());
    }

    Optional<LoadedCharacterSession> active(Player player) {
        return Optional.ofNullable(
                active.get(Objects.requireNonNull(player, "player").getUniqueId()));
    }

    Optional<LoadedCharacterSession> beginExternalValueMutation(Player player) {
        Objects.requireNonNull(player, "player");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        if (session == null || !ready(player) || !valueMutationInFlight.add(player.getUniqueId())) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> reloadExternalValueMutation(
            LoadedCharacterSession session) {
        return sessions.reload(Objects.requireNonNull(session, "session"));
    }

    void completeExternalValueMutation(
            LoadedCharacterSession previous,
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        completeSnapshotMutation(previous, result, completion);
    }

    void grantTestValue(
            Player player,
            ItemDefinition definition,
            String contentVersion,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        grantTestValue(player, definition, 1, contentVersion, completion);
    }

    void grantTestValue(
            Player player,
            ItemDefinition definition,
            int lotQuantity,
            String contentVersion,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(completion, "completion");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        int slot = firstFreeStorageSlot(player);
        if (session == null || !ready(player)) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session is not ready."));
            return;
        }
        if (slot < 0) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "No free inventory slot is available."));
            return;
        }
        if (!valueMutationInFlight.add(player.getUniqueId())) {
            completion.accept(valueMutationBusy());
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> result =
                                    sessions.grantTestValue(
                                            session, definition, slot, lotQuantity, contentVersion);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    completeSnapshotMutation(
                                                            session, result, completion));
                        });
    }

    void consumeAmmo(
            Player player,
            DefinitionId ammoDefinitionId,
            UUID projectileCommitId,
            String contentVersion,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(ammoDefinitionId, "ammoDefinitionId");
        Objects.requireNonNull(projectileCommitId, "projectileCommitId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(completion, "completion");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        if (session == null || !ready(player)) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session is not ready."));
            return;
        }
        if (!valueMutationInFlight.add(player.getUniqueId())) {
            completion.accept(valueMutationBusy());
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> result =
                                    sessions.consumeAmmo(
                                            session,
                                            ammoDefinitionId,
                                            projectileCommitId,
                                            contentVersion);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    completeSnapshotMutation(
                                                            session, result, completion));
                        });
    }

    void bindCrossbowBolt(
            Player player,
            ItemId crossbowItemId,
            DefinitionId boltDefinitionId,
            UUID operationId,
            String contentVersion,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        runDurableSnapshotMutation(
                player,
                session ->
                        sessions.bindCrossbowBolt(
                                session,
                                crossbowItemId,
                                boltDefinitionId,
                                operationId,
                                contentVersion),
                completion);
    }

    void completeCrossbowLoad(
            Player player,
            ItemId crossbowItemId,
            DefinitionId boundBoltDefinitionId,
            UUID operationId,
            String contentVersion,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        runDurableSnapshotMutation(
                player,
                session ->
                        sessions.completeCrossbowLoad(
                                session,
                                crossbowItemId,
                                boundBoltDefinitionId,
                                operationId,
                                contentVersion),
                completion);
    }

    void fireCrossbow(
            Player player,
            ItemId crossbowItemId,
            DefinitionId boundBoltDefinitionId,
            UUID projectileId,
            String contentVersion,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        runDurableSnapshotMutation(
                player,
                session ->
                        sessions.fireCrossbow(
                                session,
                                crossbowItemId,
                                boundBoltDefinitionId,
                                projectileId,
                                contentVersion),
                completion);
    }

    void commitCatalystUse(
            Player player,
            ItemId catalystItemId,
            DefinitionId expectedDefinitionId,
            int baseMaximumDurability,
            int durabilityCost,
            DefinitionId spellId,
            UUID operationId,
            String contentVersion,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        runDurableSnapshotMutation(
                player,
                session ->
                        sessions.commitCatalystUse(
                                session,
                                catalystItemId,
                                expectedDefinitionId,
                                baseMaximumDurability,
                                durabilityCost,
                                spellId,
                                operationId,
                                contentVersion),
                completion);
    }

    Optional<ItemId> equippedMainHandItemId(Player player) {
        return active(player)
                .flatMap(session -> session.snapshot().equipment().item(EquipmentSlot.MAIN_HAND));
    }

    Optional<CrossbowPersistentState> equippedCrossbowState(Player player) {
        return active(player)
                .flatMap(
                        session ->
                                session.snapshot()
                                        .equipment()
                                        .item(EquipmentSlot.MAIN_HAND)
                                        .flatMap(
                                                itemId ->
                                                        session.snapshot().itemRecords().stream()
                                                                .filter(
                                                                        record ->
                                                                                record.itemId()
                                                                                        .equals(
                                                                                                itemId))
                                                                .findFirst()))
                .map(ItemLocationRecord::payloadJson)
                .map(CrossbowPayloadCodec::decode);
    }

    Optional<CatalystDurability> equippedCatalystDurability(Player player, int baseMaximum) {
        return active(player)
                .flatMap(
                        session ->
                                session.snapshot()
                                        .equipment()
                                        .item(EquipmentSlot.MAIN_HAND)
                                        .flatMap(
                                                itemId ->
                                                        session.snapshot().itemRecords().stream()
                                                                .filter(
                                                                        record ->
                                                                                record.itemId()
                                                                                        .equals(
                                                                                                itemId))
                                                                .findFirst()))
                .map(ItemLocationRecord::payloadJson)
                .map(payload -> CatalystPayloadCodec.decode(payload, baseMaximum));
    }

    long quiverAmmoQuantity(Player player, DefinitionId definitionId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(definitionId, "definitionId");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        ItemId quiverId = session == null ? null : equippedQuiverItemId(session).orElse(null);
        return quiverId == null
                ? 0
                : QuiverAmmoLots.quantity(session.snapshot().lotRecords(), quiverId, definitionId);
    }

    long quiverUsedCapacity(Player player) {
        Objects.requireNonNull(player, "player");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        ItemId quiverId = session == null ? null : equippedQuiverItemId(session).orElse(null);
        return quiverId == null
                ? 0
                : QuiverAmmoLots.usedCapacity(session.snapshot().lotRecords(), quiverId);
    }

    List<LotLocationRecord> equippedQuiverLots(Player player) {
        Objects.requireNonNull(player, "player");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        ItemId quiverId = session == null ? null : equippedQuiverItemId(session).orElse(null);
        return quiverId == null
                ? List.of()
                : QuiverAmmoLots.all(session.snapshot().lotRecords(), quiverId);
    }

    void transferQuiverAmmo(
            Player player,
            LotId sourceLotId,
            long quantity,
            boolean store,
            UUID operationId,
            String contentVersion,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(sourceLotId, "sourceLotId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(completion, "completion");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        QuiverProfile profile = equippedQuiverProfile(player).orElse(null);
        LotLocationRecord source =
                session == null
                        ? null
                        : session.snapshot().lotRecords().stream()
                                .filter(record -> record.lotId().equals(sourceLotId))
                                .findFirst()
                                .orElse(null);
        boolean compatible =
                source != null
                        && profile != null
                        && itemEngine
                                .find(source.definitionId())
                                .flatMap(ItemDefinition::ammoProfile)
                                .filter(profile::supports)
                                .isPresent();
        long expectedQuantity =
                source == null || profile == null
                        ? 0
                        : store
                                ? Math.min(
                                        source.quantity(),
                                        Math.max(
                                                0, profile.capacity() - quiverUsedCapacity(player)))
                                : Math.min(source.quantity(), 64);
        if (session == null
                || !ready(player)
                || profile == null
                || !compatible
                || quantity != expectedQuantity
                || quantity < 1) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Quiver transfer preview is stale, incompatible or has no capacity."));
            return;
        }
        if (!valueMutationInFlight.add(player.getUniqueId())) {
            completion.accept(valueMutationBusy());
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> result =
                                    sessions.transferQuiverAmmo(
                                            session,
                                            sourceLotId,
                                            quantity,
                                            store,
                                            profile.capacity(),
                                            operationId,
                                            contentVersion);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    completeSnapshotMutation(
                                                            session, result, completion));
                        });
    }

    QuiverPreparation quiverPreparation(Player player) {
        return active(player)
                .map(session -> session.snapshot().quiverPreparation())
                .orElseGet(QuiverPreparation::empty);
    }

    Optional<QuiverProfile> equippedQuiverProfile(Player player) {
        return active(player)
                .flatMap(this::equippedQuiverDefinition)
                .flatMap(ItemDefinition::quiverProfile);
    }

    void updateQuiverPreparation(
            Player player,
            QuiverPreparation desired,
            UUID operationId,
            String contentVersion,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(desired, "desired");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(completion, "completion");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        if (session == null || !ready(player)) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session is not ready."));
            return;
        }
        ItemDefinition quiver = equippedQuiverDefinition(session).orElse(null);
        QuiverProfile profile = quiver == null ? null : quiver.quiverProfile().orElse(null);
        if (profile == null) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "An authored Quiver must be equipped first."));
            return;
        }
        if (desired.preparedAmmo().size() > profile.preparedAmmoCategoryCount()
                || desired.preparedAmmo().stream()
                                .map(itemEngine::find)
                                .flatMap(Optional::stream)
                                .map(ItemDefinition::ammoProfile)
                                .flatMap(Optional::stream)
                                .filter(profile::supports)
                                .count()
                        != desired.preparedAmmo().size()) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Prepared ammo exceeds the Quiver limit or contains an incompatible category."));
            return;
        }
        boolean unstoredAddition =
                desired.preparedAmmo().stream()
                        .filter(
                                ammo ->
                                        !session.snapshot()
                                                .quiverPreparation()
                                                .preparedAmmo()
                                                .contains(ammo))
                        .anyMatch(ammo -> quiverAmmoQuantity(player, ammo) < 1);
        if (unstoredAddition) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "New prepared ammo must already be stored in the equipped Quiver."));
            return;
        }
        if (!valueMutationInFlight.add(player.getUniqueId())) {
            completion.accept(valueMutationBusy());
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> result =
                                    sessions.updateQuiverPreparation(
                                            session, desired, operationId, contentVersion);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    completeSnapshotMutation(
                                                            session, result, completion));
                        });
    }

    void commitEquipment(
            Player player,
            EquipmentLoadout desired,
            String contentVersion,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(desired, "desired");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(completion, "completion");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        if (session == null || !ready(player)) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session is not ready."));
            return;
        }
        if (!valueMutationInFlight.add(player.getUniqueId())) {
            completion.accept(valueMutationBusy());
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> result =
                                    sessions.commitEquipment(session, desired, contentVersion);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    completeSnapshotMutation(
                                                            session, result, completion));
                        });
    }

    void commitBuild(
            Player player,
            CharacterBuild desired,
            UUID operationId,
            String contentVersion,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(desired, "desired");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(completion, "completion");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        if (session == null || !ready(player)) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session is not ready."));
            return;
        }
        if (!valueMutationInFlight.add(player.getUniqueId())) {
            completion.accept(valueMutationBusy());
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> result =
                                    sessions.commitBuild(
                                            session, desired, operationId, contentVersion);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    completeSnapshotMutation(
                                                            session, result, completion));
                        });
    }

    void commitExpeditionState(
            Player player,
            PersistentExpeditionState desired,
            UUID operationId,
            String contentVersion,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        Objects.requireNonNull(desired, "desired");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        runDurableSnapshotMutation(
                player,
                session ->
                        sessions.commitExpeditionState(
                                session, desired, operationId, contentVersion),
                completion);
    }

    void prepareFlaskAtRest(
            Player player,
            FlaskAllocation desiredAllocation,
            boolean mercyRequested,
            UUID operationId,
            String contentVersion,
            Consumer<Result<FlaskPreparationCommitResult, CharacterSessionErrorCode>> completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(desiredAllocation, "desiredAllocation");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(completion, "completion");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        if (session == null || !ready(player)) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session is not ready."));
            return;
        }
        if (!valueMutationInFlight.add(player.getUniqueId())) {
            completion.accept(valueMutationBusy());
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<FlaskPreparationCommitResult, CharacterSessionErrorCode> result =
                                    sessions.prepareFlaskAtRest(
                                            session,
                                            desiredAllocation,
                                            mercyRequested,
                                            operationId,
                                            contentVersion);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    completeFlaskPreparationMutation(
                                                            session, result, completion));
                        });
    }

    void consumeAndApplyEffect(
            Player player,
            LotId lotId,
            DefinitionId definitionId,
            ConsumableDefinitionProfile profile,
            boolean replacementConfirmed,
            UUID operationId,
            String contentVersion,
            Consumer<Result<ConsumableUseCommitResult, CharacterSessionErrorCode>> completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(completion, "completion");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        if (session == null || !ready(player)) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session is not ready."));
            return;
        }
        if (!valueMutationInFlight.add(player.getUniqueId())) {
            completion.accept(valueMutationBusy());
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<ConsumableUseCommitResult, CharacterSessionErrorCode> result =
                                    sessions.consumeAndApplyEffect(
                                            session,
                                            lotId,
                                            definitionId,
                                            profile,
                                            replacementConfirmed,
                                            operationId,
                                            contentVersion);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    completeConsumableUseMutation(
                                                            session, result, completion));
                        });
    }

    void recordProgressionEvidence(
            Player player,
            List<EvidenceCandidate> candidates,
            Consumer<Result<ProgressionEvidenceCommitResult, CharacterSessionErrorCode>>
                    completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(completion, "completion");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        if (session == null || !ready(player)) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session is not ready."));
            return;
        }
        if (!valueMutationInFlight.add(player.getUniqueId())) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                            "Another durable character mutation is in progress."));
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<ProgressionEvidenceCommitResult, CharacterSessionErrorCode>
                                    result =
                                            sessions.recordProgressionEvidence(session, candidates);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    completeProgressionMutation(
                                                            session, result, completion));
                        });
    }

    void commitTeaching(
            Player teacher,
            Player student,
            TeachingCommitRequest request,
            Consumer<Result<TeachingSessionCommitResult, CharacterSessionErrorCode>> completion) {
        Objects.requireNonNull(teacher, "teacher");
        Objects.requireNonNull(student, "student");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(completion, "completion");
        LoadedCharacterSession teacherSession = active.get(teacher.getUniqueId());
        LoadedCharacterSession studentSession = active.get(student.getUniqueId());
        if (teacherSession == null
                || studentSession == null
                || !ready(teacher)
                || !ready(student)) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Both teaching participants must have ready Player Sessions."));
            return;
        }
        UUID teacherId = teacher.getUniqueId();
        UUID studentId = student.getUniqueId();
        if (teacherId.equals(studentId)) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Teacher and student must be different players."));
            return;
        }
        if (valueMutationInFlight.contains(teacherId)
                || valueMutationInFlight.contains(studentId)) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                            "Another durable participant mutation is in progress."));
            return;
        }
        valueMutationInFlight.add(teacherId);
        valueMutationInFlight.add(studentId);
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<TeachingSessionCommitResult, CharacterSessionErrorCode> result =
                                    sessions.commitTeaching(
                                            teacherSession, studentSession, request);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    completeTeachingMutation(
                                                            teacherSession,
                                                            studentSession,
                                                            result,
                                                            completion));
                        });
    }

    void commitKnowledgeAcquisition(
            Player player,
            KnowledgeAcquisitionRequest request,
            Consumer<Result<KnowledgeAcquisitionCommitResult, CharacterSessionErrorCode>>
                    completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(completion, "completion");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        if (session == null || !ready(player)) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session is not ready."));
            return;
        }
        if (!valueMutationInFlight.add(player.getUniqueId())) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                            "Another authoritative value transaction is still in progress."));
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<KnowledgeAcquisitionCommitResult, CharacterSessionErrorCode>
                                    result = sessions.commitAcquisition(session, request);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    completeKnowledgeAcquisition(
                                                            session, result, completion));
                        });
    }

    void shutdown() {
        if (heartbeatTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(heartbeatTaskId);
            heartbeatTaskId = -1;
        }
        ArrayList<LoadedCharacterSession> closing = new ArrayList<>(active.values());
        active.clear();
        loadAttempts.clear();
        packReady.clear();
        projected.clear();
        valueMutationInFlight.clear();
        for (LoadedCharacterSession session : closing) {
            sessions.close(session);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        beginLoad(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        loadAttempts.remove(playerId);
        packReady.remove(playerId);
        projected.remove(playerId);
        valueMutationInFlight.remove(playerId);
        LoadedCharacterSession session = active.remove(playerId);
        if (session != null) {
            plugin.getServer()
                    .getScheduler()
                    .runTaskAsynchronously(plugin, () -> sessions.close(session));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            projected.remove(player.getUniqueId());
                            applyProjectionIfReady(player);
                        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!ready(event.getPlayer()) && event.hasChangedBlock()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && !ready(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && !ready(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!ready(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!ready(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!ready(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !ready(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!ready(event.getPlayer())
                && !event.getMessage()
                        .trim()
                        .toLowerCase(java.util.Locale.ROOT)
                        .startsWith("/mmo health")) {
            event.setCancelled(true);
            event.getPlayer()
                    .sendActionBar(
                            Component.text(
                                    "Character session is still loading.", NamedTextColor.YELLOW));
        }
    }

    private void beginLoad(Player player) {
        UUID playerId = player.getUniqueId();
        UUID attempt = UUID.randomUUID();
        loadAttempts.put(playerId, attempt);
        projected.remove(playerId);
        player.sendActionBar(Component.text("Loading MMO character state…", NamedTextColor.YELLOW));
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> loaded =
                                    sessions.open(playerId);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(plugin, () -> completeLoad(playerId, attempt, loaded));
                        });
    }

    private void completeLoad(
            UUID playerId,
            UUID attempt,
            Result<LoadedCharacterSession, CharacterSessionErrorCode> loaded) {
        if (!attempt.equals(loadAttempts.get(playerId))) {
            if (loaded
                    instanceof
                    Result.Success<LoadedCharacterSession, CharacterSessionErrorCode> stale) {
                plugin.getServer()
                        .getScheduler()
                        .runTaskAsynchronously(plugin, () -> sessions.close(stale.value()));
            }
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            loadAttempts.remove(playerId);
            if (loaded
                    instanceof
                    Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>
                            disconnected) {
                plugin.getServer()
                        .getScheduler()
                        .runTaskAsynchronously(plugin, () -> sessions.close(disconnected.value()));
            }
            return;
        }
        if (loaded
                instanceof
                Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode> failure) {
            loadAttempts.remove(playerId);
            player.sendMessage(
                    Component.text(
                            "MMO character remains locked: "
                                    + failure.error().code()
                                    + " "
                                    + failure.detail(),
                            NamedTextColor.RED));
            return;
        }
        LoadedCharacterSession session =
                ((Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>) loaded)
                        .value();
        active.put(playerId, session);
        applyProjectionIfReady(player);
    }

    private void applyProjectionIfReady(Player player) {
        applyProjectionIfReady(player, true);
    }

    private void applyProjectionIfReady(Player player, boolean notifyReadyHandlers) {
        UUID playerId = player.getUniqueId();
        LoadedCharacterSession session = active.get(playerId);
        if (session == null || !packReady.contains(playerId) || projected.contains(playerId)) {
            return;
        }
        Result<ProjectionApplyReport, ProjectionApplyErrorCode> applied =
                projections.reconcile(player, session.snapshot(), itemEngine);
        if (applied
                instanceof
                Result.Failure<ProjectionApplyReport, ProjectionApplyErrorCode> failure) {
            player.sendMessage(
                    Component.text(
                            "Inventory projection remains locked: "
                                    + failure.error().code()
                                    + " "
                                    + failure.detail(),
                            NamedTextColor.RED));
            return;
        }
        ProjectionApplyReport report =
                ((Result.Success<ProjectionApplyReport, ProjectionApplyErrorCode>) applied).value();
        projected.add(playerId);
        player.sendActionBar(
                Component.text(
                        "MMO character ready"
                                + (report.changed() ? " — inventory projection repaired" : ""),
                        NamedTextColor.GREEN));
        if (notifyReadyHandlers) {
            readyHandlers.forEach(handler -> handler.accept(player));
        }
    }

    private void heartbeatAll() {
        for (LoadedCharacterSession session : List.copyOf(active.values())) {
            plugin.getServer()
                    .getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                Result<LoadedCharacterSession, CharacterSessionErrorCode> result =
                                        sessions.heartbeat(session);
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(plugin, () -> completeHeartbeat(session, result));
                            });
        }
    }

    private void completeHeartbeat(
            LoadedCharacterSession previous,
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result) {
        UUID playerId = previous.characterId().value();
        LoadedCharacterSession current = active.get(playerId);
        if (current == null || !current.sessionId().equals(previous.sessionId())) {
            return;
        }
        if (result
                instanceof
                Result.Success<LoadedCharacterSession, CharacterSessionErrorCode> success) {
            active.put(playerId, current.withLease(success.value().lease()));
            return;
        }
        Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode> failure =
                (Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>) result;
        active.remove(playerId);
        projected.remove(playerId);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.kick(
                    Component.text(
                            "MMO session lease lost: " + failure.detail(), NamedTextColor.RED));
        }
    }

    private void completeSnapshotMutation(
            LoadedCharacterSession previous,
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        UUID playerId = previous.characterId().value();
        valueMutationInFlight.remove(playerId);
        LoadedCharacterSession current = active.get(playerId);
        if (current == null || !current.sessionId().equals(previous.sessionId())) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session changed before value mutation completed."));
            return;
        }
        if (result instanceof Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>) {
            completion.accept(result);
            return;
        }
        LoadedCharacterSession mutationResult =
                ((Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>) result)
                        .value();
        LoadedCharacterSession updated = current.withSnapshot(mutationResult.snapshot());
        active.put(playerId, updated);
        projected.remove(playerId);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            applyProjectionIfReady(player, false);
        }
        completion.accept(Result.success(updated));
    }

    private void completeProgressionMutation(
            LoadedCharacterSession previous,
            Result<ProgressionEvidenceCommitResult, CharacterSessionErrorCode> result,
            Consumer<Result<ProgressionEvidenceCommitResult, CharacterSessionErrorCode>>
                    completion) {
        UUID playerId = previous.characterId().value();
        valueMutationInFlight.remove(playerId);
        LoadedCharacterSession current = active.get(playerId);
        if (current == null || !current.sessionId().equals(previous.sessionId())) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session changed before progression commit completed."));
            return;
        }
        if (result
                instanceof
                Result.Failure<ProgressionEvidenceCommitResult, CharacterSessionErrorCode>) {
            completion.accept(result);
            return;
        }
        ProgressionEvidenceCommitResult committed =
                ((Result.Success<ProgressionEvidenceCommitResult, CharacterSessionErrorCode>)
                                result)
                        .value();
        LoadedCharacterSession updated = current.withSnapshot(committed.session().snapshot());
        active.put(playerId, updated);
        completion.accept(
                Result.success(
                        new ProgressionEvidenceCommitResult(updated, committed.executions())));
    }

    private void completeFlaskPreparationMutation(
            LoadedCharacterSession previous,
            Result<FlaskPreparationCommitResult, CharacterSessionErrorCode> result,
            Consumer<Result<FlaskPreparationCommitResult, CharacterSessionErrorCode>> completion) {
        UUID playerId = previous.characterId().value();
        valueMutationInFlight.remove(playerId);
        LoadedCharacterSession current = active.get(playerId);
        if (current == null || !current.sessionId().equals(previous.sessionId())) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session changed before Flask preparation completed."));
            return;
        }
        if (result
                instanceof
                Result.Failure<FlaskPreparationCommitResult, CharacterSessionErrorCode>) {
            completion.accept(result);
            return;
        }
        FlaskPreparationCommitResult committed =
                ((Result.Success<FlaskPreparationCommitResult, CharacterSessionErrorCode>) result)
                        .value();
        LoadedCharacterSession updated = current.withSnapshot(committed.session().snapshot());
        active.put(playerId, updated);
        projected.remove(playerId);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            applyProjectionIfReady(player, false);
        }
        completion.accept(
                Result.success(
                        new FlaskPreparationCommitResult(
                                updated, committed.preparation(), committed.replayed())));
    }

    private void completeConsumableUseMutation(
            LoadedCharacterSession previous,
            Result<ConsumableUseCommitResult, CharacterSessionErrorCode> result,
            Consumer<Result<ConsumableUseCommitResult, CharacterSessionErrorCode>> completion) {
        UUID playerId = previous.characterId().value();
        valueMutationInFlight.remove(playerId);
        LoadedCharacterSession current = active.get(playerId);
        if (current == null || !current.sessionId().equals(previous.sessionId())) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session changed before consumable use completed."));
            return;
        }
        if (result
                instanceof Result.Failure<ConsumableUseCommitResult, CharacterSessionErrorCode>) {
            completion.accept(result);
            return;
        }
        ConsumableUseCommitResult committed =
                ((Result.Success<ConsumableUseCommitResult, CharacterSessionErrorCode>) result)
                        .value();
        LoadedCharacterSession updated = current.withSnapshot(committed.session().snapshot());
        active.put(playerId, updated);
        projected.remove(playerId);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            applyProjectionIfReady(player, false);
        }
        completion.accept(
                Result.success(
                        new ConsumableUseCommitResult(
                                updated, committed.effect(), committed.replayed())));
    }

    private void completeKnowledgeAcquisition(
            LoadedCharacterSession previous,
            Result<KnowledgeAcquisitionCommitResult, CharacterSessionErrorCode> result,
            Consumer<Result<KnowledgeAcquisitionCommitResult, CharacterSessionErrorCode>>
                    completion) {
        UUID playerId = previous.characterId().value();
        valueMutationInFlight.remove(playerId);
        LoadedCharacterSession current = active.get(playerId);
        if (current == null || !current.sessionId().equals(previous.sessionId())) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session changed before Knowledge acquisition completed."));
            return;
        }
        if (result
                instanceof
                Result.Failure<KnowledgeAcquisitionCommitResult, CharacterSessionErrorCode>) {
            completion.accept(result);
            return;
        }
        KnowledgeAcquisitionCommitResult committed =
                ((Result.Success<KnowledgeAcquisitionCommitResult, CharacterSessionErrorCode>)
                                result)
                        .value();
        LoadedCharacterSession updated = current.withSnapshot(committed.session().snapshot());
        active.put(playerId, updated);
        completion.accept(
                Result.success(
                        new KnowledgeAcquisitionCommitResult(updated, committed.execution())));
    }

    private void completeTeachingMutation(
            LoadedCharacterSession previousTeacher,
            LoadedCharacterSession previousStudent,
            Result<TeachingSessionCommitResult, CharacterSessionErrorCode> result,
            Consumer<Result<TeachingSessionCommitResult, CharacterSessionErrorCode>> completion) {
        UUID teacherId = previousTeacher.characterId().value();
        UUID studentId = previousStudent.characterId().value();
        valueMutationInFlight.remove(teacherId);
        valueMutationInFlight.remove(studentId);
        LoadedCharacterSession currentTeacher = active.get(teacherId);
        LoadedCharacterSession currentStudent = active.get(studentId);
        if (currentTeacher == null
                || currentStudent == null
                || !currentTeacher.sessionId().equals(previousTeacher.sessionId())
                || !currentStudent.sessionId().equals(previousStudent.sessionId())) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "A participant session changed before teaching commit completed."));
            return;
        }
        if (result
                instanceof Result.Failure<TeachingSessionCommitResult, CharacterSessionErrorCode>) {
            completion.accept(result);
            return;
        }
        TeachingSessionCommitResult committed =
                ((Result.Success<TeachingSessionCommitResult, CharacterSessionErrorCode>) result)
                        .value();
        LoadedCharacterSession updatedTeacher =
                currentTeacher.withSnapshot(committed.teacherSession().snapshot());
        LoadedCharacterSession updatedStudent =
                currentStudent.withSnapshot(committed.studentSession().snapshot());
        active.put(teacherId, updatedTeacher);
        active.put(studentId, updatedStudent);
        completion.accept(
                Result.success(
                        new TeachingSessionCommitResult(
                                updatedTeacher, updatedStudent, committed.execution())));
    }

    private void runDurableSnapshotMutation(
            Player player,
            java.util.function.Function<
                            LoadedCharacterSession,
                            Result<LoadedCharacterSession, CharacterSessionErrorCode>>
                    mutation,
            Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>> completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(completion, "completion");
        LoadedCharacterSession session = active.get(player.getUniqueId());
        if (session == null || !ready(player)) {
            completion.accept(
                    Result.failure(
                            CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                            "Character session is not ready."));
            return;
        }
        if (!valueMutationInFlight.add(player.getUniqueId())) {
            completion.accept(valueMutationBusy());
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> result =
                                    mutation.apply(session);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    completeSnapshotMutation(
                                                            session, result, completion));
                        });
    }

    private static <T> Result<T, CharacterSessionErrorCode> valueMutationBusy() {
        return Result.failure(
                CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                "Another authoritative value transaction is still in progress.");
    }

    private static int firstFreeStorageSlot(Player player) {
        org.bukkit.inventory.ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            org.bukkit.inventory.ItemStack item = storage[slot];
            if (slot != ChronicleService.HOTBAR_SLOT && (item == null || item.getType().isAir())) {
                return slot;
            }
        }
        return -1;
    }

    private Optional<ItemDefinition> equippedQuiverDefinition(LoadedCharacterSession session) {
        return session.snapshot()
                .equipment()
                .item(EquipmentSlot.QUIVER)
                .flatMap(
                        itemId ->
                                session.snapshot().itemRecords().stream()
                                        .filter(record -> record.itemId().equals(itemId))
                                        .findFirst())
                .flatMap(record -> itemEngine.find(record.definitionId()));
    }

    private static Optional<ItemId> equippedQuiverItemId(LoadedCharacterSession session) {
        return session.snapshot().equipment().item(EquipmentSlot.QUIVER);
    }
}
