package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.items.projection.ExpectedProjection;
import com.branz.mmorpg.items.projection.ProjectionValueType;
import com.branz.mmorpg.items.quiver.QuiverPreparation;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocationType;
import com.branz.mmorpg.scenes.QuiverAmmoTransferPreview;
import com.branz.mmorpg.scenes.QuiverTransferDirection;
import com.branz.mmorpg.scenes.SceneCloseReason;
import com.branz.mmorpg.scenes.SceneErrorCode;
import com.branz.mmorpg.scenes.SceneMode;
import com.branz.mmorpg.scenes.ScenePreviewState;
import com.branz.mmorpg.scenes.SceneSession;
import com.branz.mmorpg.scenes.SceneSessionManager;
import com.branz.mmorpg.scenes.preview.CompactScenePreviewProvider;
import com.branz.mmorpg.scenes.preview.ScenePreviewHandle;
import com.branz.mmorpg.scenes.preview.ScenePreviewProvider;
import java.time.Clock;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

final class SceneHubController implements Listener {
    private static final int INVENTORY_SIZE = 54;
    private static final Map<SceneMode, ButtonSpec> HUB_BUTTONS = hubButtons();

    private final JavaPlugin plugin;
    private final BootstrapLifecycle lifecycle;
    private final ResourcePackGate packGate;
    private final ChronicleService chronicle;
    private final CharacterSessionController characterSessions;
    private final ItemEngine itemEngine;
    private final String contentVersion;
    private final SceneSessionManager sessions = new SceneSessionManager(Clock.systemUTC());
    private final ScenePreviewProvider previewProvider = new CompactScenePreviewProvider();
    private final NamespacedKey actionKey;
    private final Map<UUID, ScenePreviewHandle> previewHandles = new HashMap<>();
    private final Set<UUID> navigating = new HashSet<>();
    private final Set<UUID> committing = new HashSet<>();

    SceneHubController(
            JavaPlugin plugin,
            BootstrapLifecycle lifecycle,
            ResourcePackGate packGate,
            ChronicleService chronicle,
            CharacterSessionController characterSessions,
            ItemEngine itemEngine,
            String contentVersion) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.packGate = Objects.requireNonNull(packGate, "packGate");
        this.chronicle = Objects.requireNonNull(chronicle, "chronicle");
        this.characterSessions = Objects.requireNonNull(characterSessions, "characterSessions");
        this.itemEngine = Objects.requireNonNull(itemEngine, "itemEngine");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        actionKey = new NamespacedKey(plugin, "scene_action");
    }

    Result<SceneSession, SceneErrorCode> open(Player player) {
        Objects.requireNonNull(player, "player");
        Result<EligibilityAccepted, SceneErrorCode> eligibility = checkEligibility(player);
        if (!eligibility.isSuccess()) {
            Result.Failure<EligibilityAccepted, SceneErrorCode> failure =
                    (Result.Failure<EligibilityAccepted, SceneErrorCode>) eligibility;
            player.sendActionBar(Component.text(failure.detail(), NamedTextColor.RED));
            return Result.failure(failure.error(), failure.detail());
        }

        Result<SceneSession, SceneErrorCode> opened =
                sessions.open(
                        player.getUniqueId(),
                        characterSessions
                                .active(player)
                                .map(session -> session.snapshot().equipment())
                                .orElse(EquipmentLoadout.empty()),
                        characterSessions.quiverPreparation(player));
        if (!opened.isSuccess()) {
            return opened;
        }
        SceneSession session = ((Result.Success<SceneSession, SceneErrorCode>) opened).value();
        Result<ScenePreviewHandle, SceneErrorCode> preview = previewProvider.open(session);
        if (preview instanceof Result.Failure<ScenePreviewHandle, SceneErrorCode> failure) {
            sessions.closeCurrent(player.getUniqueId(), SceneCloseReason.PROVIDER_FAILURE);
            return Result.failure(failure.error(), failure.detail());
        }
        previewHandles.put(
                player.getUniqueId(),
                ((Result.Success<ScenePreviewHandle, SceneErrorCode>) preview).value());
        openPage(player, session);
        return Result.success(session);
    }

    void shutdown() {
        for (ScenePreviewHandle handle : previewHandles.values()) {
            previewProvider.close(handle);
        }
        previewHandles.clear();
        navigating.clear();
        committing.clear();
        sessions.closeAll(SceneCloseReason.PLUGIN_DISABLE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChronicleUse(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || !event.getAction().isRightClick()
                || !chronicle.isChronicle(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        open(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SceneInventoryHolder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        String action = action(event.getCurrentItem());
        if (action == null) {
            return;
        }
        if ("exit".equals(action)) {
            interrupt(player, SceneCloseReason.EXIT, true);
            return;
        }
        if ("back".equals(action)) {
            Result<SceneSession, SceneErrorCode> result =
                    sessions.back(player.getUniqueId(), holder.sessionId());
            result.map(
                    session -> {
                        navigate(player, session);
                        return session;
                    });
            return;
        }
        if (action.startsWith("equip-main:")) {
            previewMainHand(player, holder, action.substring("equip-main:".length()));
            return;
        }
        if (action.startsWith("equip-quiver:")) {
            previewQuiver(player, holder, action.substring("equip-quiver:".length()));
            return;
        }
        if (action.startsWith("prepare-ammo:")) {
            previewPreparedAmmo(player, holder, action.substring("prepare-ammo:".length()));
            return;
        }
        if (action.startsWith("store-quiver:")) {
            previewQuiverStore(player, holder, action.substring("store-quiver:".length()));
            return;
        }
        if (action.startsWith("quiver-lot:")) {
            String[] parts = action.substring("quiver-lot:".length()).split(":", 2);
            if (parts.length != 2) {
                return;
            }
            if (event.isRightClick()) {
                previewQuiverWithdrawal(player, holder, parts[0]);
            } else {
                previewPreparedAmmo(player, holder, parts[1]);
            }
            return;
        }
        if ("confirm-equipment".equals(action)) {
            confirmEquipment(player, holder);
            return;
        }
        SceneMode mode;
        try {
            mode = SceneMode.valueOf(action);
        } catch (IllegalArgumentException exception) {
            return;
        }
        Result<SceneSession, SceneErrorCode> result =
                sessions.changeMode(player.getUniqueId(), holder.sessionId(), mode);
        result.map(
                session -> {
                    navigate(player, session);
                    return session;
                });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SceneInventoryHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SceneInventoryHolder)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (navigating.remove(player.getUniqueId())) {
            return;
        }
        closeState(player, SceneCloseReason.INVENTORY_CLOSED);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            interrupt(player, SceneCloseReason.DAMAGE, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            interrupt(event.getPlayer(), SceneCloseReason.MOVEMENT, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        interrupt(event.getPlayer(), SceneCloseReason.TELEPORT, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        interrupt(event.getPlayer(), SceneCloseReason.WORLD_CHANGE, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        interrupt(event.getEntity(), SceneCloseReason.DEATH, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        interrupt(event.getPlayer(), SceneCloseReason.DISCONNECT, false);
    }

    private Result<EligibilityAccepted, SceneErrorCode> checkEligibility(Player player) {
        if (sessions.find(player.getUniqueId()).isPresent()) {
            return Result.failure(SceneErrorCode.SCENE_ALREADY_OPEN, "Scene Hub is already open.");
        }
        if (!lifecycle.acceptsSessions()) {
            return Result.failure(SceneErrorCode.SCENE_NOT_ELIGIBLE, "MMO session is not ready.");
        }
        if (!packGate.ready(player)) {
            return Result.failure(SceneErrorCode.SCENE_NOT_ELIGIBLE, "Resource pack is not ready.");
        }
        if (!characterSessions.ready(player)) {
            return Result.failure(
                    SceneErrorCode.SCENE_NOT_ELIGIBLE, "Character database session is not ready.");
        }
        if (player.isDead()
                || !player.wouldCollideUsing(player.getBoundingBox().shift(0.0, -0.05, 0.0))
                || player.isFlying()
                || player.isSwimming()
                || player.isInsideVehicle()
                || player.getFallDistance() > 0
                || player.getVelocity().lengthSquared() > 0.04) {
            return Result.failure(
                    SceneErrorCode.SCENE_NOT_ELIGIBLE,
                    "Stand still on safe ground to open the Scene Hub.");
        }
        return Result.success(EligibilityAccepted.INSTANCE);
    }

    private void navigate(Player player, SceneSession session) {
        navigating.add(player.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> openPage(player, session));
    }

    private void openPage(Player player, SceneSession session) {
        SceneInventoryHolder holder = new SceneInventoryHolder(session.sessionId(), session.mode());
        Component title =
                session.mode() == SceneMode.HUB
                        ? Component.text("Adventurer's Chronicle", NamedTextColor.GOLD)
                        : Component.text(modeTitle(session.mode()), NamedTextColor.GOLD);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title);
        holder.attach(inventory);
        if (session.mode() == SceneMode.HUB) {
            HUB_BUTTONS.forEach(
                    (mode, spec) ->
                            inventory.setItem(
                                    spec.slot(),
                                    button(spec.material(), spec.label(), mode.name())));
            inventory.setItem(49, button(Material.BARRIER, "Exit Chronicle", "exit"));
        } else {
            if (session.mode() == SceneMode.EQUIPMENT) {
                populateEquipmentPage(player, inventory, session);
            } else {
                inventory.setItem(
                        22,
                        button(
                                Material.PAPER,
                                modeTitle(session.mode()) + " shell is ready",
                                "noop"));
            }
            inventory.setItem(45, button(Material.ARROW, "Back", "back"));
            inventory.setItem(53, button(Material.BARRIER, "Exit Chronicle", "exit"));
        }
        player.openInventory(inventory);
    }

    private void populateEquipmentPage(
            Player player, Inventory inventory, SceneSession sceneSession) {
        LoadedCharacterSession character = characterSessions.active(player).orElse(null);
        if (character == null) {
            inventory.setItem(
                    22, button(Material.BARRIER, "Character session unavailable", "noop"));
            return;
        }
        int buttonSlot = 10;
        Set<com.branz.mmorpg.api.identity.DefinitionId> storedDefinitions = new HashSet<>();
        for (LotLocationRecord lot : characterSessions.equippedQuiverLots(player)) {
            if (!storedDefinitions.add(lot.definitionId())) {
                continue;
            }
            if (buttonSlot > 34) {
                break;
            }
            boolean prepared =
                    sceneSession
                            .previewState()
                            .quiverPreparation()
                            .preparedAmmo()
                            .contains(lot.definitionId());
            inventory.setItem(
                    buttonSlot++,
                    button(
                            Material.SPECTRAL_ARROW,
                            (prepared ? "[Quiver Prepared] " : "[Quiver Stored] ")
                                    + lot.definitionId().value()
                                    + " x"
                                    + characterSessions.quiverAmmoQuantity(
                                            player, lot.definitionId())
                                    + " | L prepare / R withdraw",
                            "quiver-lot:"
                                    + lot.lotId().value()
                                    + ":"
                                    + lot.definitionId().value()));
        }
        for (com.branz.mmorpg.api.identity.DefinitionId preparedId :
                sceneSession.previewState().quiverPreparation().preparedAmmo()) {
            ItemDefinition definition = itemEngine.find(preparedId).orElse(null);
            if (definition == null
                    || definition.ammoProfile().isEmpty()
                    || storedDefinitions.contains(preparedId)
                    || buttonSlot > 34) {
                continue;
            }
            inventory.setItem(
                    buttonSlot++,
                    button(
                            Material.ARROW,
                            "[Prepared empty] " + preparedId.value() + " | click to remove",
                            "prepare-ammo:" + preparedId.value()));
        }
        Set<com.branz.mmorpg.api.identity.DefinitionId> inventoryDefinitions = new HashSet<>();
        for (ExpectedProjection projection : character.snapshot().inventory()) {
            if (buttonSlot > 34) {
                continue;
            }
            ItemDefinition definition = itemEngine.find(projection.definitionId()).orElse(null);
            if (definition == null) {
                continue;
            }
            if (projection.valueType() == ProjectionValueType.STACKABLE_LOT
                    && definition.ammoProfile().isPresent()) {
                if (!inventoryDefinitions.add(definition.id())) {
                    continue;
                }
                inventory.setItem(
                        buttonSlot++,
                        button(
                                Material.ARROW,
                                "[Inventory -> Quiver] "
                                        + definition.id().value()
                                        + " x"
                                        + projection.quantity(),
                                "store-quiver:" + projection.valueId()));
                continue;
            }
            if (projection.valueType() != ProjectionValueType.UNIQUE_ITEM) {
                continue;
            }
            if (definition.quiverProfile().isPresent()) {
                inventory.setItem(
                        buttonSlot++,
                        button(
                                Material.LEATHER,
                                definition.id().value()
                                        + " #"
                                        + projection.valueId().toString().substring(0, 8),
                                "equip-quiver:" + projection.valueId()));
                continue;
            }
            if (definition.weaponProfile().isEmpty()) {
                continue;
            }
            inventory.setItem(
                    buttonSlot++,
                    button(
                            Material.IRON_SWORD,
                            projection.definitionId().value()
                                    + " #"
                                    + projection.valueId().toString().substring(0, 8),
                            "equip-main:" + projection.valueId()));
        }
        String committed =
                sceneSession
                        .committedState()
                        .equipment()
                        .item(EquipmentSlot.MAIN_HAND)
                        .map(item -> item.value().toString().substring(0, 8))
                        .orElse("empty");
        String preview =
                sceneSession
                        .previewState()
                        .equipment()
                        .item(EquipmentSlot.MAIN_HAND)
                        .map(item -> item.value().toString().substring(0, 8))
                        .orElse("empty");
        inventory.setItem(
                37, button(Material.PAPER, "Main hand: " + committed + " -> " + preview, "noop"));
        String committedQuiver =
                sceneSession
                        .committedState()
                        .equipment()
                        .item(EquipmentSlot.QUIVER)
                        .map(item -> item.value().toString().substring(0, 8))
                        .orElse("empty");
        String previewQuiver =
                sceneSession
                        .previewState()
                        .equipment()
                        .item(EquipmentSlot.QUIVER)
                        .map(item -> item.value().toString().substring(0, 8))
                        .orElse("empty");
        inventory.setItem(
                38,
                button(
                        Material.LEATHER,
                        "Quiver: " + committedQuiver + " -> " + previewQuiver,
                        "noop"));
        inventory.setItem(
                39,
                button(
                        Material.ARROW,
                        "Quiver load "
                                + characterSessions.quiverUsedCapacity(player)
                                + "/"
                                + characterSessions
                                        .equippedQuiverProfile(player)
                                        .map(profile -> Integer.toString(profile.capacity()))
                                        .orElse("0")
                                + " | Prepared: "
                                + sceneSession.previewState().quiverPreparation().preparedAmmo()
                                + " selected="
                                + sceneSession
                                        .previewState()
                                        .quiverPreparation()
                                        .selectedAmmo()
                                        .map(com.branz.mmorpg.api.identity.DefinitionId::value)
                                        .orElse("none"),
                        "noop"));
        inventory.setItem(
                41,
                button(
                        Material.HOPPER,
                        sceneSession
                                .previewState()
                                .quiverTransfer()
                                .map(
                                        transfer ->
                                                "Pending "
                                                        + transfer.direction().name()
                                                        + " x"
                                                        + transfer.quantity()
                                                        + " lot="
                                                        + transfer.sourceLotId()
                                                                .toString()
                                                                .substring(0, 8))
                                .orElse("No Quiver lot transfer preview"),
                        "noop"));
        inventory.setItem(
                40,
                button(
                        Material.LIME_DYE,
                        sceneSession.hasUncommittedPreview()
                                ? "Confirm Scene transaction"
                                : "No Scene change",
                        "confirm-equipment"));
    }

    private void previewMainHand(Player player, SceneInventoryHolder holder, String itemUuid) {
        previewEquipment(player, holder, itemUuid, EquipmentSlot.MAIN_HAND);
    }

    private void previewQuiver(Player player, SceneInventoryHolder holder, String itemUuid) {
        previewEquipment(player, holder, itemUuid, EquipmentSlot.QUIVER);
    }

    private void previewEquipment(
            Player player, SceneInventoryHolder holder, String itemUuid, EquipmentSlot slot) {
        try {
            ItemId itemId = new ItemId(UUID.fromString(itemUuid));
            Result<SceneSession, SceneErrorCode> result =
                    sessions.previewEquipment(
                            player.getUniqueId(),
                            holder.sessionId(),
                            slot,
                            java.util.Optional.of(itemId));
            result.map(
                    session -> {
                        navigate(player, session);
                        return session;
                    });
        } catch (IllegalArgumentException exception) {
            player.sendActionBar(
                    Component.text("Invalid equipment selection.", NamedTextColor.RED));
        }
    }

    private void previewPreparedAmmo(
            Player player, SceneInventoryHolder holder, String definitionId) {
        try {
            com.branz.mmorpg.api.identity.DefinitionId ammoId =
                    com.branz.mmorpg.api.identity.DefinitionId.of(definitionId);
            ItemDefinition ammo =
                    itemEngine
                            .find(ammoId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Ammo definition is unavailable."));
            com.branz.mmorpg.items.definition.QuiverProfile profile =
                    characterSessions
                            .equippedQuiverProfile(player)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Equip and confirm a Quiver first."));
            if (ammo.ammoProfile().filter(profile::supports).isEmpty()) {
                throw new IllegalArgumentException(
                        "Ammo is incompatible with the equipped Quiver.");
            }
            SceneSession current = sessions.find(player.getUniqueId()).orElseThrow();
            boolean alreadyPrepared =
                    current.previewState().quiverPreparation().preparedAmmo().contains(ammoId);
            if (!alreadyPrepared && characterSessions.quiverAmmoQuantity(player, ammoId) < 1) {
                throw new IllegalArgumentException(
                        "Store this ammo in the equipped Quiver before preparing it.");
            }
            QuiverPreparation desired =
                    current.previewState()
                            .quiverPreparation()
                            .toggle(ammoId, profile.preparedAmmoCategoryCount());
            Result<SceneSession, SceneErrorCode> result =
                    sessions.previewQuiverPreparation(
                            player.getUniqueId(), holder.sessionId(), desired);
            result.map(
                    session -> {
                        navigate(player, session);
                        return session;
                    });
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendActionBar(Component.text(exception.getMessage(), NamedTextColor.RED));
        }
    }

    private void previewQuiverStore(
            Player player, SceneInventoryHolder holder, String sourceLotUuid) {
        try {
            LotId lotId = new LotId(UUID.fromString(sourceLotUuid));
            LoadedCharacterSession character = characterSessions.active(player).orElseThrow();
            LotLocationRecord source =
                    character.snapshot().lotRecords().stream()
                            .filter(record -> record.lotId().equals(lotId))
                            .findFirst()
                            .orElseThrow();
            com.branz.mmorpg.items.definition.QuiverProfile profile =
                    characterSessions
                            .equippedQuiverProfile(player)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Equip and confirm a Quiver first."));
            boolean compatible =
                    source.location().type() == ValueLocationType.CHARACTER_INVENTORY
                            && itemEngine
                                    .find(source.definitionId())
                                    .flatMap(ItemDefinition::ammoProfile)
                                    .filter(profile::supports)
                                    .isPresent();
            long available = profile.capacity() - characterSessions.quiverUsedCapacity(player);
            long quantity = Math.min(source.quantity(), Math.max(0, available));
            if (!compatible || quantity < 1) {
                throw new IllegalArgumentException(
                        compatible
                                ? "The equipped Quiver is full."
                                : "This lot is not compatible inventory ammo.");
            }
            previewQuiverTransfer(
                    player,
                    holder,
                    new QuiverAmmoTransferPreview(
                            lotId.value(), quantity, QuiverTransferDirection.STORE));
        } catch (IllegalArgumentException | java.util.NoSuchElementException exception) {
            player.sendActionBar(
                    Component.text(
                            exception.getMessage() == null || exception.getMessage().isBlank()
                                    ? "Quiver store preview is stale or invalid."
                                    : exception.getMessage(),
                            NamedTextColor.RED));
        }
    }

    private void previewQuiverWithdrawal(
            Player player, SceneInventoryHolder holder, String sourceLotUuid) {
        try {
            LotId lotId = new LotId(UUID.fromString(sourceLotUuid));
            LotLocationRecord source =
                    characterSessions.equippedQuiverLots(player).stream()
                            .filter(record -> record.lotId().equals(lotId))
                            .findFirst()
                            .orElseThrow();
            previewQuiverTransfer(
                    player,
                    holder,
                    new QuiverAmmoTransferPreview(
                            lotId.value(),
                            Math.min(source.quantity(), 64),
                            QuiverTransferDirection.WITHDRAW));
        } catch (IllegalArgumentException | java.util.NoSuchElementException exception) {
            player.sendActionBar(
                    Component.text(
                            exception.getMessage() == null || exception.getMessage().isBlank()
                                    ? "Quiver withdrawal preview is stale or invalid."
                                    : exception.getMessage(),
                            NamedTextColor.RED));
        }
    }

    private void previewQuiverTransfer(
            Player player, SceneInventoryHolder holder, QuiverAmmoTransferPreview transfer) {
        Result<SceneSession, SceneErrorCode> result =
                sessions.previewQuiverTransfer(player.getUniqueId(), holder.sessionId(), transfer);
        result.map(
                session -> {
                    navigate(player, session);
                    return session;
                });
    }

    private void confirmEquipment(Player player, SceneInventoryHolder holder) {
        SceneSession sceneSession = sessions.find(player.getUniqueId()).orElse(null);
        if (sceneSession == null
                || !sceneSession.sessionId().equals(holder.sessionId())
                || !committing.add(player.getUniqueId())) {
            return;
        }
        boolean equipmentChanged =
                !sceneSession
                        .committedState()
                        .equipment()
                        .equals(sceneSession.previewState().equipment());
        boolean preparationChanged =
                !sceneSession
                        .committedState()
                        .quiverPreparation()
                        .equals(sceneSession.previewState().quiverPreparation());
        boolean transferChanged = sceneSession.previewState().quiverTransfer().isPresent();
        int mutationCount =
                (equipmentChanged ? 1 : 0)
                        + (preparationChanged ? 1 : 0)
                        + (transferChanged ? 1 : 0);
        if (mutationCount > 1) {
            committing.remove(player.getUniqueId());
            player.sendActionBar(
                    Component.text(
                            "Confirm equipment, lot transfer and preparation as separate transactions.",
                            NamedTextColor.RED));
            return;
        }
        if (mutationCount == 0) {
            committing.remove(player.getUniqueId());
            return;
        }
        player.sendActionBar(
                Component.text("Committing Scene transaction...", NamedTextColor.YELLOW));
        java.util.function.Consumer<Result<LoadedCharacterSession, CharacterSessionErrorCode>>
                completion =
                        result -> {
                            committing.remove(player.getUniqueId());
                            if (result
                                    instanceof
                                    Result.Failure<
                                                    LoadedCharacterSession,
                                                    CharacterSessionErrorCode>
                                            failure) {
                                player.sendMessage(
                                        Component.text(
                                                "Scene transaction rejected: "
                                                        + failure.error().code()
                                                        + " "
                                                        + failure.detail(),
                                                NamedTextColor.RED));
                                return;
                            }
                            LoadedCharacterSession updated =
                                    ((Result.Success<
                                                            LoadedCharacterSession,
                                                            CharacterSessionErrorCode>)
                                                    result)
                                            .value();
                            Result<SceneSession, SceneErrorCode> confirmed =
                                    sessions.confirm(
                                            player.getUniqueId(),
                                            holder.sessionId(),
                                            ignored ->
                                                    Result.success(
                                                            new ScenePreviewState(
                                                                    updated.snapshot().equipment(),
                                                                    updated.snapshot()
                                                                            .quiverPreparation())));
                            if (confirmed
                                    instanceof
                                    Result.Success<SceneSession, SceneErrorCode> success) {
                                player.sendActionBar(
                                        Component.text(
                                                preparationChanged
                                                        ? "Quiver preparation committed."
                                                        : transferChanged
                                                                ? "Quiver lot transfer committed."
                                                                : "Equipment committed.",
                                                NamedTextColor.GREEN));
                                navigate(player, success.value());
                            }
                        };
        if (transferChanged) {
            QuiverAmmoTransferPreview transfer =
                    sceneSession.previewState().quiverTransfer().orElseThrow();
            characterSessions.transferQuiverAmmo(
                    player,
                    new LotId(transfer.sourceLotId()),
                    transfer.quantity(),
                    transfer.direction() == QuiverTransferDirection.STORE,
                    UUID.randomUUID(),
                    contentVersion,
                    completion);
        } else if (preparationChanged) {
            characterSessions.updateQuiverPreparation(
                    player,
                    sceneSession.previewState().quiverPreparation(),
                    UUID.randomUUID(),
                    contentVersion,
                    completion);
        } else {
            characterSessions.commitEquipment(
                    player, sceneSession.previewState().equipment(), contentVersion, completion);
        }
    }

    private ItemStack button(Material material, String label, String action) {
        ItemStack item = new ItemStack(material);
        item.editMeta(
                meta -> {
                    meta.displayName(Component.text(label, NamedTextColor.YELLOW));
                    meta.getPersistentDataContainer()
                            .set(actionKey, PersistentDataType.STRING, action);
                });
        return item;
    }

    private String action(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta()
                .getPersistentDataContainer()
                .get(actionKey, PersistentDataType.STRING);
    }

    private void interrupt(Player player, SceneCloseReason reason, boolean closeInventory) {
        if (sessions.find(player.getUniqueId()).isEmpty()) {
            return;
        }
        closeState(player, reason);
        if (closeInventory
                && player.getOpenInventory().getTopInventory().getHolder()
                        instanceof SceneInventoryHolder) {
            player.closeInventory();
        }
    }

    private void closeState(Player player, SceneCloseReason reason) {
        ScenePreviewHandle handle = previewHandles.remove(player.getUniqueId());
        if (handle != null) {
            previewProvider.close(handle);
        }
        navigating.remove(player.getUniqueId());
        committing.remove(player.getUniqueId());
        sessions.closeCurrent(player.getUniqueId(), reason);
    }

    private static String modeTitle(SceneMode mode) {
        return switch (mode) {
            case HUB -> "Adventurer's Chronicle";
            case EQUIPMENT -> "Character & Equipment";
            case WARDROBE_DYE -> "Wardrobe & Dye";
            case COMBAT_ARTS -> "Combat Arts";
            case MAGIC_ATTUNEMENT -> "Magic & Attunement";
            case JOURNAL_PENDING_REWARDS -> "Journal & Pending Rewards";
            case SETTINGS_HELP -> "Settings & Help";
        };
    }

    private static Map<SceneMode, ButtonSpec> hubButtons() {
        EnumMap<SceneMode, ButtonSpec> buttons = new EnumMap<>(SceneMode.class);
        buttons.put(
                SceneMode.EQUIPMENT,
                new ButtonSpec(10, Material.IRON_CHESTPLATE, "Character & Equipment"));
        buttons.put(
                SceneMode.WARDROBE_DYE,
                new ButtonSpec(12, Material.LEATHER_CHESTPLATE, "Wardrobe & Dye"));
        buttons.put(SceneMode.COMBAT_ARTS, new ButtonSpec(14, Material.IRON_SWORD, "Combat Arts"));
        buttons.put(
                SceneMode.MAGIC_ATTUNEMENT,
                new ButtonSpec(16, Material.ENCHANTED_BOOK, "Magic & Attunement"));
        buttons.put(
                SceneMode.JOURNAL_PENDING_REWARDS,
                new ButtonSpec(29, Material.CHEST, "Journal & Pending Rewards"));
        buttons.put(
                SceneMode.SETTINGS_HELP,
                new ButtonSpec(33, Material.COMPARATOR, "Settings & Help"));
        return Map.copyOf(buttons);
    }

    private record ButtonSpec(int slot, Material material, String label) {}

    private enum EligibilityAccepted {
        INSTANCE
    }
}
