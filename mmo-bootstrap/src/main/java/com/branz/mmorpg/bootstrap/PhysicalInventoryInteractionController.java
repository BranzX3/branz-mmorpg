package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.projection.InventoryProjectionMovePlanner;
import com.branz.mmorpg.items.projection.ObservedProjection;
import com.branz.mmorpg.items.projection.ProjectionMoveDisposition;
import com.branz.mmorpg.items.projection.ProjectionMoveErrorCode;
import com.branz.mmorpg.items.projection.ProjectionMoveIntent;
import com.branz.mmorpg.items.projection.ProjectionMovePlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Owns physical inventory movement for durable unique items and whole stackable lots within player
 * storage slots 0-35 while database placement remains authoritative.
 */
final class PhysicalInventoryInteractionController implements Listener {
    private static final int STORAGE_SIZE = 36;
    private static final int CURSOR_OBSERVATION_SLOT = 1000;
    private static final int FIRST_GAMEPLAY_HOTBAR_SLOT = 0;
    private static final int LAST_GAMEPLAY_HOTBAR_SLOT = 7;

    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private final BukkitItemProjectionCodec codec;
    private final PhysicalInventoryItemMoveService moves;
    private final PhysicalInventoryLotMoveService lotMoves;
    private final String contentVersion;
    private final Map<UUID, PendingInteraction> pending = new HashMap<>();
    private final Map<UUID, HotbarAcceptanceProgress> hotbarAcceptanceProgress = new HashMap<>();

    PhysicalInventoryInteractionController(
            JavaPlugin plugin,
            CharacterSessionController characters,
            BukkitItemProjectionCodec codec,
            PhysicalInventoryItemMoveService moves,
            PhysicalInventoryLotMoveService lotMoves,
            String contentVersion) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.moves = Objects.requireNonNull(moves, "moves");
        this.lotMoves = Objects.requireNonNull(lotMoves, "lotMoves");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !characters.ready(player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (Boolean.getBoolean("mmo.physical-hotbar-acceptance")) {
            plugin.getLogger()
                    .info(
                            "PHYSICAL_AUTHORITY_HOTBAR_CLICK_HANDLER_SERVER player="
                                    + player.getName()
                                    + " action="
                                    + event.getAction()
                                    + " click="
                                    + event.getClick()
                                    + " rawSlot="
                                    + event.getRawSlot()
                                    + " slot="
                                    + event.getSlot()
                                    + " cancelled="
                                    + event.isCancelled()
                                    + " currentProjection="
                                    + hasProjection(event.getCurrentItem())
                                    + " cursorProjection="
                                    + hasProjection(player.getItemOnCursor()));
        }
        PendingInteraction interaction = pending.get(playerId);
        boolean touchesMmo =
                hasProjection(event.getCurrentItem()) || hasProjection(player.getItemOnCursor());
        if (interaction == null && !touchesMmo) {
            return;
        }
        if (interaction != null && interaction.phase() != PendingPhase.INTERACTING) {
            event.setCancelled(true);
            return;
        }
        if (!supported(event, player)) {
            event.setCancelled(true);
            player.sendActionBar(
                    Component.text(
                            "This MMO inventory action is not available in the physical-item slice yet.",
                            NamedTextColor.RED));
            return;
        }
        if (interaction == null) {
            LoadedCharacterSession session =
                    characters.beginExternalValueMutation(player).orElse(null);
            if (session == null) {
                event.setCancelled(true);
                player.sendActionBar(
                        Component.text(
                                "Another durable character transaction is still in progress.",
                                NamedTextColor.RED));
                return;
            }
            interaction =
                    new PendingInteraction(session, UUID.randomUUID(), PendingPhase.INTERACTING);
            pending.put(playerId, interaction);
        }
        scheduleObservation(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void observeHotbarAcceptanceClick(InventoryClickEvent event) {
        if (!Boolean.getBoolean("mmo.physical-hotbar-acceptance")
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        plugin.getLogger()
                .info(
                        "PHYSICAL_AUTHORITY_HOTBAR_CLICK_MONITOR_SERVER player="
                                + player.getName()
                                + " action="
                                + event.getAction()
                                + " click="
                                + event.getClick()
                                + " rawSlot="
                                + event.getRawSlot()
                                + " slot="
                                + event.getSlot()
                                + " cancelled="
                                + event.isCancelled()
                                + " sameInventory="
                                + (event.getClickedInventory() == player.getInventory())
                                + " currentProjection="
                                + hasProjection(event.getCurrentItem())
                                + " eventCursorProjection="
                                + hasProjection(event.getCursor())
                                + " playerCursorProjection="
                                + hasProjection(player.getItemOnCursor()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !characters.ready(player)) {
            return;
        }
        boolean touchesMmo =
                pending.containsKey(player.getUniqueId())
                        || hasProjection(player.getItemOnCursor())
                        || event.getNewItems().values().stream().anyMatch(this::hasProjection);
        if (!touchesMmo) {
            return;
        }
        event.setCancelled(true);
        player.sendActionBar(
                Component.text(
                        "Move MMO items with normal inventory clicks for now.",
                        NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        PendingInteraction interaction = pending.get(player.getUniqueId());
        if (interaction == null || interaction.phase() != PendingPhase.INTERACTING) {
            return;
        }
        pending.put(
                player.getUniqueId(),
                new PendingInteraction(
                        interaction.session(), interaction.operationId(), PendingPhase.ABORTING));
        plugin.getServer()
                .getScheduler()
                .runTask(plugin, () -> abortInteraction(player.getUniqueId(), null));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        PendingInteraction interaction = pending.remove(event.getPlayer().getUniqueId());
        if (interaction != null) {
            clearMmoCursor(event.getPlayer());
        }
    }

    void shutdown() {
        for (UUID playerId : List.copyOf(pending.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                clearMmoCursor(player);
            }
        }
        pending.clear();
    }

    private void scheduleObservation(UUID playerId) {
        plugin.getServer().getScheduler().runTask(plugin, () -> observe(playerId));
    }

    private void observe(UUID playerId) {
        PendingInteraction interaction = pending.get(playerId);
        if (interaction == null || interaction.phase() != PendingPhase.INTERACTING) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            pending.remove(playerId);
            return;
        }
        Result<PhysicalInventoryObservation, ProjectionMoveErrorCode> observation = observe(player);
        if (observation
                instanceof
                Result.Failure<PhysicalInventoryObservation, ProjectionMoveErrorCode> failure) {
            abortInteraction(playerId, failure.error().code() + ": " + failure.detail());
            return;
        }
        PhysicalInventoryObservation observed =
                ((Result.Success<PhysicalInventoryObservation, ProjectionMoveErrorCode>)
                                observation)
                        .value();
        if (Boolean.getBoolean("mmo.physical-hotbar-acceptance")) {
            plugin.getLogger()
                    .info(
                            "PHYSICAL_AUTHORITY_HOTBAR_OBSERVATION_SERVER player="
                                    + player.getName()
                                    + " storageCount="
                                    + observed.storage().size()
                                    + " cursorPresent="
                                    + observed.cursor().isPresent());
        }
        Result<ProjectionMovePlan, ProjectionMoveErrorCode> planned =
                InventoryProjectionMovePlanner.plan(
                        interaction.session().snapshot().inventory(),
                        observed.storage(),
                        observed.cursor(),
                        STORAGE_SIZE,
                        ChronicleService.HOTBAR_SLOT);
        if (planned
                instanceof Result.Failure<ProjectionMovePlan, ProjectionMoveErrorCode> failure) {
            if (Boolean.getBoolean("mmo.physical-hotbar-acceptance")) {
                plugin.getLogger()
                        .severe(
                                "PHYSICAL_AUTHORITY_HOTBAR_PLAN_FAILED_SERVER player="
                                        + player.getName()
                                        + " code="
                                        + failure.error().code()
                                        + " detail="
                                        + failure.detail());
            }
            abortInteraction(playerId, failure.error().code() + ": " + failure.detail());
            return;
        }
        ProjectionMovePlan plan =
                ((Result.Success<ProjectionMovePlan, ProjectionMoveErrorCode>) planned).value();
        if (Boolean.getBoolean("mmo.physical-hotbar-acceptance")) {
            plugin.getLogger()
                    .info(
                            "PHYSICAL_AUTHORITY_HOTBAR_PLAN_SERVER player="
                                    + player.getName()
                                    + " disposition="
                                    + plan.disposition()
                                    + " intent="
                                    + plan.intent()
                                            .map(
                                                    intent ->
                                                            intent.sourceSlot()
                                                                    + "->"
                                                                    + intent.destinationSlot()
                                                                    + ":"
                                                                    + intent.valueId())
                                            .orElse("none"));
        }
        if (plan.disposition() == ProjectionMoveDisposition.TRANSIENT_CURSOR) {
            return;
        }
        if (plan.disposition() == ProjectionMoveDisposition.UNCHANGED) {
            abortInteraction(playerId, null);
            return;
        }
        commit(player, interaction, plan.intent().orElseThrow());
    }

    private Result<PhysicalInventoryObservation, ProjectionMoveErrorCode> observe(Player player) {
        List<ObservedProjection> storage = new ArrayList<>();
        for (int slot = 0; slot < STORAGE_SIZE; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!hasProjection(stack)) {
                continue;
            }
            ObservedProjection observed = codec.decode(stack, slot).orElse(null);
            if (observed == null) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_INVALID,
                        "Storage contains an MMO projection whose signature cannot be verified.");
            }
            storage.add(observed);
        }
        ItemStack cursorStack = player.getItemOnCursor();
        Optional<ObservedProjection> cursor = Optional.empty();
        if (hasProjection(cursorStack)) {
            ObservedProjection observed =
                    codec.decode(cursorStack, CURSOR_OBSERVATION_SLOT).orElse(null);
            if (observed == null) {
                return Result.failure(
                        ProjectionMoveErrorCode.PROJECTION_MOVE_INVALID,
                        "Cursor contains an MMO projection whose signature cannot be verified.");
            }
            cursor = Optional.of(observed);
        }
        return Result.success(new PhysicalInventoryObservation(List.copyOf(storage), cursor));
    }

    private void commit(
            Player player, PendingInteraction interaction, ProjectionMoveIntent intent) {
        UUID playerId = player.getUniqueId();
        if (Boolean.getBoolean("mmo.physical-hotbar-acceptance")) {
            plugin.getLogger()
                    .info(
                            "PHYSICAL_AUTHORITY_HOTBAR_COMMIT_BEGIN_SERVER player="
                                    + player.getName()
                                    + " source="
                                    + intent.sourceSlot()
                                    + " destination="
                                    + intent.destinationSlot()
                                    + " value="
                                    + intent.valueId());
        }
        if (!pending.replace(
                playerId,
                interaction,
                new PendingInteraction(
                        interaction.session(),
                        interaction.operationId(),
                        PendingPhase.COMMITTING))) {
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> result =
                                    switch (intent.valueType()) {
                                        case UNIQUE_ITEM ->
                                                moves.moveUniqueItem(
                                                        interaction.session(),
                                                        new ItemId(intent.valueId()),
                                                        intent.sourceSlot(),
                                                        intent.destinationSlot(),
                                                        interaction.operationId(),
                                                        contentVersion);
                                        case STACKABLE_LOT ->
                                                lotMoves.moveFullLot(
                                                        interaction.session(),
                                                        new LotId(intent.valueId()),
                                                        intent.sourceSlot(),
                                                        intent.destinationSlot(),
                                                        interaction.operationId(),
                                                        contentVersion);
                                    };
                            String originalFailure = null;
                            if (result
                                    instanceof
                                    Result.Failure<
                                                    LoadedCharacterSession,
                                                    CharacterSessionErrorCode>
                                            failure) {
                                originalFailure = failure.error().code() + ": " + failure.detail();
                                result =
                                        characters.reloadExternalValueMutation(
                                                interaction.session());
                            }
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> completed =
                                    result;
                            String failureMessage = originalFailure;
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    finishCommit(
                                                            playerId,
                                                            interaction,
                                                            intent,
                                                            completed,
                                                            failureMessage));
                        });
    }

    private void finishCommit(
            UUID playerId,
            PendingInteraction interaction,
            ProjectionMoveIntent intent,
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result,
            String originalFailure) {
        pending.remove(playerId);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            clearMmoCursor(player);
        }
        characters.completeExternalValueMutation(
                interaction.session(),
                result,
                completed -> {
                    if (player == null || !player.isOnline()) {
                        return;
                    }
                    if (completed
                            instanceof
                            Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>
                                    failure) {
                        player.kick(
                                Component.text(
                                        "Inventory authority could not be reloaded: "
                                                + failure.detail()
                                                + ". Reconnect after database recovery.",
                                        NamedTextColor.RED));
                        return;
                    }
                    if (originalFailure != null) {
                        player.sendActionBar(
                                Component.text(
                                        "Inventory move rejected and reconciled: "
                                                + originalFailure,
                                        NamedTextColor.RED));
                        return;
                    }
                    if (Boolean.getBoolean("mmo.physical-hotbar-acceptance")
                            && completed
                                    instanceof
                                    Result.Success<
                                                    LoadedCharacterSession,
                                                    CharacterSessionErrorCode>
                                            success) {
                        recordHotbarAcceptanceMove(player, success.value(), intent);
                    }
                });
    }

    private void recordHotbarAcceptanceMove(
            Player player, LoadedCharacterSession completed, ProjectionMoveIntent intent) {
        if (intent.valueType()
                != com.branz.mmorpg.items.projection.ProjectionValueType.UNIQUE_ITEM) {
            return;
        }
        String expectedReference = "slot:" + intent.destinationSlot();
        boolean authoritative =
                completed.snapshot().itemRecords().stream()
                        .anyMatch(
                                record ->
                                        record.itemId().value().equals(intent.valueId())
                                                && record.location().type()
                                                        == com.branz.mmorpg.persistence.transaction
                                                                .ValueLocationType
                                                                .CHARACTER_INVENTORY
                                                && record.location()
                                                        .reference()
                                                        .filter(expectedReference::equals)
                                                        .isPresent());
        if (!authoritative) {
            plugin.getLogger()
                    .severe(
                            "PHYSICAL_AUTHORITY_HOTBAR_MOVE_VERIFY_FAILED_SERVER player="
                                    + player.getName()
                                    + " value="
                                    + intent.valueId()
                                    + " destination="
                                    + intent.destinationSlot());
            return;
        }
        plugin.getLogger()
                .info(
                        "PHYSICAL_AUTHORITY_HOTBAR_MOVE_COMMITTED_SERVER player="
                                + player.getName()
                                + " value="
                                + intent.valueId()
                                + " source="
                                + intent.sourceSlot()
                                + " destination="
                                + intent.destinationSlot());
        if (!gameplayHotbarSlot(intent.sourceSlot())
                || !gameplayHotbarSlot(intent.destinationSlot())
                || intent.sourceSlot() == intent.destinationSlot()) {
            plugin.getLogger()
                    .severe(
                            "PHYSICAL_AUTHORITY_HOTBAR_SEQUENCE_INVALID_SERVER player="
                                    + player.getName()
                                    + " value="
                                    + intent.valueId()
                                    + " source="
                                    + intent.sourceSlot()
                                    + " destination="
                                    + intent.destinationSlot());
            return;
        }
        UUID playerId = player.getUniqueId();
        HotbarAcceptanceProgress progress = hotbarAcceptanceProgress.get(playerId);
        if (progress == null) {
            if (intent.sourceSlot() != FIRST_GAMEPLAY_HOTBAR_SLOT) {
                plugin.getLogger()
                        .severe(
                                "PHYSICAL_AUTHORITY_HOTBAR_SEQUENCE_INVALID_SERVER player="
                                        + player.getName()
                                        + " expectedSource="
                                        + FIRST_GAMEPLAY_HOTBAR_SLOT
                                        + " actualSource="
                                        + intent.sourceSlot());
                return;
            }
            hotbarAcceptanceProgress.put(
                    playerId,
                    new HotbarAcceptanceProgress(intent.valueId(), intent.destinationSlot()));
            player.setLevel(9);
            return;
        }
        if (!progress.valueId().equals(intent.valueId())
                || intent.sourceSlot() != progress.currentSlot()) {
            plugin.getLogger()
                    .severe(
                            "PHYSICAL_AUTHORITY_HOTBAR_SEQUENCE_INVALID_SERVER player="
                                    + player.getName()
                                    + " expectedValue="
                                    + progress.valueId()
                                    + " actualValue="
                                    + intent.valueId()
                                    + " expectedSource="
                                    + progress.currentSlot()
                                    + " actualSource="
                                    + intent.sourceSlot());
            return;
        }
        hotbarAcceptanceProgress.remove(playerId);
        player.setLevel(10);
    }

    private void abortInteraction(UUID playerId, String detail) {
        PendingInteraction interaction = pending.remove(playerId);
        if (interaction == null || interaction.phase() == PendingPhase.COMMITTING) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            clearMmoCursor(player);
        }
        characters.completeExternalValueMutation(
                interaction.session(),
                Result.success(interaction.session()),
                completed -> {
                    if (player != null && player.isOnline() && detail != null) {
                        player.sendActionBar(
                                Component.text(
                                        "Inventory move reverted: " + detail, NamedTextColor.RED));
                    }
                });
    }

    private boolean supported(InventoryClickEvent event, Player player) {
        if (event.getClickedInventory() != player.getInventory()
                || event.getSlot() < 0
                || event.getSlot() >= STORAGE_SIZE
                || event.getSlot() == ChronicleService.HOTBAR_SLOT) {
            return false;
        }
        return PhysicalInventoryInteractionPolicy.supportsStorageAction(event.getAction().name());
    }

    private static boolean gameplayHotbarSlot(int slot) {
        return slot >= FIRST_GAMEPLAY_HOTBAR_SLOT && slot <= LAST_GAMEPLAY_HOTBAR_SLOT;
    }

    private boolean hasProjection(ItemStack stack) {
        return codec.hasProjectionMarker(stack);
    }

    private void clearMmoCursor(Player player) {
        if (hasProjection(player.getItemOnCursor())) {
            player.setItemOnCursor(new ItemStack(Material.AIR));
        }
    }

    private enum PendingPhase {
        INTERACTING,
        COMMITTING,
        ABORTING
    }

    private record HotbarAcceptanceProgress(UUID valueId, int currentSlot) {
        private HotbarAcceptanceProgress {
            Objects.requireNonNull(valueId, "valueId");
        }
    }

    private record PendingInteraction(
            LoadedCharacterSession session, UUID operationId, PendingPhase phase) {
        private PendingInteraction {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(phase, "phase");
        }
    }

    private record PhysicalInventoryObservation(
            List<ObservedProjection> storage, Optional<ObservedProjection> cursor) {
        private PhysicalInventoryObservation {
            storage = List.copyOf(Objects.requireNonNull(storage, "storage"));
            Objects.requireNonNull(cursor, "cursor");
        }
    }
}
