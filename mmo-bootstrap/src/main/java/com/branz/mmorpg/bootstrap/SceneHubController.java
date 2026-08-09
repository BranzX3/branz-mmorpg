package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.resource.FlaskAllocation;
import com.branz.mmorpg.combat.resource.FlaskDose;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.items.definition.WeaponCombatProfile;
import com.branz.mmorpg.items.definition.WeaponLoadoutErrorCode;
import com.branz.mmorpg.items.definition.WeaponLoadoutPolicy;
import com.branz.mmorpg.items.definition.WeaponLoadoutResolution;
import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.items.projection.ExpectedProjection;
import com.branz.mmorpg.items.projection.ProjectionValueType;
import com.branz.mmorpg.items.quiver.QuiverPreparation;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocationType;
import com.branz.mmorpg.progression.build.AttunableEffectDefinition;
import com.branz.mmorpg.progression.build.BuildEngine;
import com.branz.mmorpg.progression.build.BuildErrorCode;
import com.branz.mmorpg.progression.build.BuildResolution;
import com.branz.mmorpg.progression.build.CharacterBuild;
import com.branz.mmorpg.progression.build.FormDefinition;
import com.branz.mmorpg.progression.build.TechniqueDefinition;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import com.branz.mmorpg.scenes.QuiverAmmoTransferPreview;
import com.branz.mmorpg.scenes.QuiverTransferDirection;
import com.branz.mmorpg.scenes.SceneCloseReason;
import com.branz.mmorpg.scenes.SceneEngine;
import com.branz.mmorpg.scenes.SceneErrorCode;
import com.branz.mmorpg.scenes.SceneMode;
import com.branz.mmorpg.scenes.ScenePreviewState;
import com.branz.mmorpg.scenes.SceneProfiles;
import com.branz.mmorpg.scenes.SceneRecovery;
import com.branz.mmorpg.scenes.SceneSession;
import com.branz.mmorpg.scenes.SceneSessionManager;
import com.branz.mmorpg.scenes.overlay.MenuOverlay;
import com.branz.mmorpg.scenes.overlay.MenuOverlayHandle;
import java.time.Clock;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

final class SceneHubController implements Listener, MenuOverlay {
    private static final int INVENTORY_SIZE = 54;
    private static final Map<SceneMode, ButtonSpec> HUB_BUTTONS = hubButtons();

    private final JavaPlugin plugin;
    private final BootstrapLifecycle lifecycle;
    private final ResourcePackGate packGate;
    private final ChronicleService chronicle;
    private final CharacterSessionController characterSessions;
    private final ItemEngine itemEngine;
    private final BuildEngine buildEngine;
    private final CombatSessionController combat;
    private final double restContextSpawnRadiusSquared;
    private final String contentVersion;
    private final SceneSessionManager sessions = new SceneSessionManager(Clock.systemUTC());
    private final BukkitPreviewActorProvider previewActors;
    private final SceneEngine sceneEngine;
    private final NamespacedKey actionKey;
    private final Map<UUID, FlaskAllocation> flaskPreviews = new HashMap<>();
    private final Set<UUID> navigating = new HashSet<>();
    private final Set<UUID> committing = new HashSet<>();
    private final Set<UUID> recovering = new HashSet<>();
    private final int validityTaskId;

    SceneHubController(
            JavaPlugin plugin,
            BootstrapLifecycle lifecycle,
            ResourcePackGate packGate,
            ChronicleService chronicle,
            CharacterSessionController characterSessions,
            ItemEngine itemEngine,
            BuildEngine buildEngine,
            CombatSessionController combat,
            String contentVersion) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.packGate = Objects.requireNonNull(packGate, "packGate");
        this.chronicle = Objects.requireNonNull(chronicle, "chronicle");
        this.characterSessions = Objects.requireNonNull(characterSessions, "characterSessions");
        this.itemEngine = Objects.requireNonNull(itemEngine, "itemEngine");
        this.buildEngine = Objects.requireNonNull(buildEngine, "buildEngine");
        this.combat = Objects.requireNonNull(combat, "combat");
        double restRadius =
                plugin.getConfig().getDouble("scene.rest-context-spawn-radius-blocks", 16.0);
        if (!Double.isFinite(restRadius) || restRadius <= 0) {
            throw new IllegalArgumentException("scene rest-context spawn radius must be positive");
        }
        restContextSpawnRadiusSquared = restRadius * restRadius;
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        actionKey = new NamespacedKey(plugin, "scene_action");
        previewActors = new BukkitPreviewActorProvider(plugin, this::updatePreviewActor);
        sceneEngine =
                new SceneEngine(
                        new BukkitLocalSceneEnvironmentProvider(plugin),
                        previewActors,
                        new BukkitLocalSceneViewpointProvider(plugin),
                        this,
                        new SceneRecovery());
        validityTaskId =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(plugin, this::validateActiveSessions, 5L, 5L)
                        .getTaskId();
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
                        SceneProfiles.localCharacterHub(),
                        characterSessions
                                .active(player)
                                .map(session -> session.snapshot().equipment())
                                .orElse(EquipmentLoadout.empty()),
                        characterSessions.quiverPreparation(player),
                        characterSessions
                                .active(player)
                                .map(session -> session.snapshot().build())
                                .orElseGet(CharacterBuild::initial));
        if (!opened.isSuccess()) {
            return opened;
        }
        SceneSession session = ((Result.Success<SceneSession, SceneErrorCode>) opened).value();
        Result<com.branz.mmorpg.scenes.ScenePresentation, SceneErrorCode> presentation =
                sceneEngine.open(session);
        if (presentation
                instanceof
                Result.Failure<com.branz.mmorpg.scenes.ScenePresentation, SceneErrorCode> failure) {
            sessions.closeCurrent(player.getUniqueId(), SceneCloseReason.PROVIDER_FAILURE);
            player.sendActionBar(Component.text(failure.detail(), NamedTextColor.RED));
            return Result.failure(failure.error(), failure.detail());
        }
        return Result.success(session);
    }

    void shutdown() {
        plugin.getServer().getScheduler().cancelTask(validityTaskId);
        try {
            sceneEngine.closeAll();
        } catch (RuntimeException exception) {
            plugin.getLogger()
                    .warning("Scene provider recovery failed during shutdown: " + exception);
        } finally {
            flaskPreviews.clear();
            navigating.clear();
            committing.clear();
            recovering.clear();
            sessions.closeAll(SceneCloseReason.PLUGIN_DISABLE);
        }
    }

    private void validateActiveSessions() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (sessions.find(player.getUniqueId()).isEmpty()) {
                continue;
            }
            if (!characterSessions.ready(player) || !packGate.ready(player)) {
                interrupt(player, SceneCloseReason.SESSION_INVALIDATED, true);
                continue;
            }
            CombatSessionStatus status = combat.status(player).orElse(null);
            if (status == null) {
                interrupt(player, SceneCloseReason.SESSION_INVALIDATED, true);
            } else if (status.engagementState()
                    == com.branz.mmorpg.combat.state.EngagementState.ENGAGED) {
                interrupt(player, SceneCloseReason.HOSTILE_STATE, true);
            }
        }
    }

    @Override
    public Result<MenuOverlayHandle, SceneErrorCode> open(SceneSession session) {
        Player player = plugin.getServer().getPlayer(session.playerId());
        if (player == null || !player.isOnline()) {
            return Result.failure(
                    SceneErrorCode.SCENE_PROVIDER_FAILURE,
                    "Inventory control overlay requires a live player.");
        }
        openPage(player, session);
        return Result.success(
                new MenuOverlayHandle(session.sessionId(), session.playerId(), "bukkit-inventory"));
    }

    @Override
    public Result<MenuOverlayHandle, SceneErrorCode> update(
            MenuOverlayHandle handle, SceneSession session) {
        if (!handle.sessionId().equals(session.sessionId())
                || !handle.viewerId().equals(session.playerId())) {
            return Result.failure(
                    SceneErrorCode.SCENE_STALE_SESSION,
                    "Menu overlay belongs to another Scene session.");
        }
        Player player = plugin.getServer().getPlayer(session.playerId());
        if (player == null || !player.isOnline()) {
            return Result.failure(
                    SceneErrorCode.SCENE_PROVIDER_FAILURE,
                    "Inventory control overlay lost its player.");
        }
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            SceneSession current = sessions.find(player.getUniqueId()).orElse(null);
                            if (current == null
                                    || !current.sessionId().equals(session.sessionId())
                                    || current.revision() != session.revision()) {
                                navigating.remove(player.getUniqueId());
                                return;
                            }
                            navigating.add(player.getUniqueId());
                            openPage(player, session);
                        });
        return Result.success(handle);
    }

    @Override
    public void close(MenuOverlayHandle handle) {
        Player player = plugin.getServer().getPlayer(handle.viewerId());
        if (player != null
                && player.isOnline()
                && player.getOpenInventory().getTopInventory().getHolder()
                        instanceof SceneInventoryHolder holder
                && holder.sessionId().equals(handle.sessionId())) {
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
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
        if (action.startsWith("equip-offhand:")) {
            previewOffHand(player, holder, action.substring("equip-offhand:".length()));
            return;
        }
        if ("unequip-offhand".equals(action)) {
            previewEquipment(player, holder, Optional.empty(), EquipmentSlot.OFF_HAND);
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
        if (action.startsWith("technique:")) {
            previewTechnique(player, holder, action.substring("technique:".length()));
            return;
        }
        if (action.startsWith("form:")) {
            previewForm(player, holder, action.substring("form:".length()));
            return;
        }
        if ("clear-form".equals(action)) {
            previewForm(player, holder, null);
            return;
        }
        if (action.startsWith("attune:")) {
            previewAttunement(player, holder, action.substring("attune:".length()));
            return;
        }
        if ("confirm-build".equals(action)) {
            confirmBuild(player, holder);
            return;
        }
        if (action.startsWith("flask-adjust:")) {
            adjustFlaskAllocation(player, holder, action.substring("flask-adjust:".length()));
            return;
        }
        if ("confirm-flask".equals(action)) {
            confirmFlaskPreparation(player, holder);
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
        interrupt(player, SceneCloseReason.INVENTORY_CLOSED, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            interrupt(player, SceneCloseReason.DAMAGE, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }
        SceneSession session = sessions.find(event.getPlayer().getUniqueId()).orElse(null);
        if (session == null || !session.profile().locksNormalMovement()) {
            return;
        }
        org.bukkit.Input input = event.getPlayer().getCurrentInput();
        boolean normalInput =
                input.isForward()
                        || input.isBackward()
                        || input.isLeft()
                        || input.isRight()
                        || input.isJump()
                        || input.isSneak();
        if (normalInput) {
            event.setCancelled(true);
        } else {
            interrupt(event.getPlayer(), SceneCloseReason.FORCED_MOVEMENT, true);
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        previewActors.hideActiveActorsFrom(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreviewActorRemoved(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Mannequin)
                || !event.getEntity()
                        .getScoreboardTags()
                        .contains(BukkitPreviewActorProvider.ACTOR_TAG)) {
            return;
        }
        previewActors
                .viewerForActor(event.getEntity().getUniqueId())
                .map(plugin.getServer()::getPlayer)
                .filter(Objects::nonNull)
                .ifPresent(player -> interrupt(player, SceneCloseReason.ACTOR_INVALIDATED, true));
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
        CombatSessionStatus combatStatus = combat.status(player).orElse(null);
        if (combatStatus == null) {
            return Result.failure(
                    SceneErrorCode.SCENE_NOT_ELIGIBLE, "Combat session is not ready.");
        }
        if (combatStatus.engagementState()
                == com.branz.mmorpg.combat.state.EngagementState.ENGAGED) {
            return Result.failure(
                    SceneErrorCode.SCENE_NOT_ELIGIBLE,
                    "Local Character Scene is unavailable while ENGAGED.");
        }
        if (combatStatus.dead()
                || combatStatus.weaponState() != com.branz.mmorpg.combat.state.WeaponState.SHEATHED
                || combatStatus.actionPhase().isPresent()
                || combatStatus.bowDrawPhase().isPresent()
                || combatStatus.bowRecoveryTicksRemaining() > 0
                || combatStatus.crossbowRecoveryTicksRemaining() > 0
                || combatStatus.crossbowCheckpointCommitPending()
                || combatStatus.spellCastPhase().isPresent()
                || combatStatus.spellCommitPending()
                || combatStatus.bowAmmoCommitPending()
                || combatStatus.ammoSwitchHandlingTicksRemaining() > 0
                || combatStatus.dodgePhase().isPresent()
                || combatStatus.guardPhase() != com.branz.mmorpg.combat.guard.GuardPhase.INACTIVE
                || combatStatus.crowdControl().isPresent()) {
            return Result.failure(
                    SceneErrorCode.SCENE_NOT_ELIGIBLE,
                    "Finish the current action and sheathe the weapon before opening the Scene.");
        }
        return Result.success(EligibilityAccepted.INSTANCE);
    }

    private void navigate(Player player, SceneSession session) {
        Result<com.branz.mmorpg.scenes.ScenePresentation, SceneErrorCode> updated =
                sceneEngine.update(session);
        if (updated
                instanceof
                Result.Failure<com.branz.mmorpg.scenes.ScenePresentation, SceneErrorCode> failure) {
            player.sendActionBar(Component.text(failure.detail(), NamedTextColor.RED));
            interrupt(player, SceneCloseReason.PROVIDER_FAILURE, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        if (event.getVelocity().lengthSquared() > 0.0001) {
            interrupt(event.getPlayer(), SceneCloseReason.FORCED_MOVEMENT, true);
        }
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
            } else if (session.mode() == SceneMode.COMBAT_ARTS) {
                populateCombatArtsPage(player, inventory, session);
            } else if (session.mode() == SceneMode.FORMS) {
                populateFormsPage(player, inventory, session);
            } else if (session.mode() == SceneMode.MAGIC_ATTUNEMENT) {
                populateMagicAttunementPage(player, inventory, session);
            } else if (session.mode() == SceneMode.CHARACTER_INFORMATION) {
                populateCharacterInformationPage(player, inventory, session);
            } else if (session.mode() == SceneMode.FLASK_PREPARATION) {
                populateFlaskPreparationPage(player, inventory);
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
            if (definition.shieldProfile().isPresent()) {
                inventory.setItem(
                        buttonSlot++,
                        button(
                                Material.SHIELD,
                                definition.id().value()
                                        + " #"
                                        + projection.valueId().toString().substring(0, 8),
                                "equip-offhand:" + projection.valueId()));
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
        String committedOffHand =
                sceneSession
                        .committedState()
                        .equipment()
                        .item(EquipmentSlot.OFF_HAND)
                        .map(item -> item.value().toString().substring(0, 8))
                        .orElse("empty");
        String previewOffHand =
                sceneSession
                        .previewState()
                        .equipment()
                        .item(EquipmentSlot.OFF_HAND)
                        .map(item -> item.value().toString().substring(0, 8))
                        .orElse("empty");
        inventory.setItem(
                36,
                button(
                        Material.SHIELD,
                        "Off hand: " + committedOffHand + " -> " + previewOffHand,
                        "noop"));
        inventory.setItem(
                42, button(Material.GRAY_DYE, "Preview empty off hand", "unequip-offhand"));
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

    private void populateCombatArtsPage(
            Player player, Inventory inventory, SceneSession sceneSession) {
        String family = previewWeaponFamily(player, sceneSession).orElse(null);
        if (family == null) {
            inventory.setItem(
                    22,
                    button(
                            Material.BARRIER,
                            "Equip a supported weapon before preparing Combat Arts",
                            "noop"));
            return;
        }
        int slot = 10;
        for (TechniqueDefinition technique : buildEngine.techniques()) {
            if (!technique.supports(family) || slot > 25) {
                continue;
            }
            boolean selected =
                    technique
                            .id()
                            .equals(
                                    sceneSession
                                            .previewState()
                                            .build()
                                            .techniques()
                                            .get(technique.branch()));
            inventory.setItem(
                    slot++,
                    button(
                            selected ? Material.LIME_DYE : Material.IRON_SWORD,
                            (selected ? "Selected: " : "Technique: ")
                                    + technique.id().value()
                                    + " ["
                                    + technique.branch().name()
                                    + "]",
                            "technique:" + technique.id().value()));
        }
        populateBuildCommitStatus(player, inventory, sceneSession, family);
    }

    private void populateFormsPage(Player player, Inventory inventory, SceneSession sceneSession) {
        String family = previewWeaponFamily(player, sceneSession).orElse(null);
        if (family == null) {
            inventory.setItem(
                    22,
                    button(
                            Material.BARRIER,
                            "Equip a supported weapon before selecting a Form",
                            "noop"));
            return;
        }
        int slot = 10;
        for (FormDefinition form : buildEngine.forms()) {
            if (!form.supports(family) || slot > 25) {
                continue;
            }
            boolean selected =
                    sceneSession
                            .previewState()
                            .build()
                            .form()
                            .filter(form.id()::equals)
                            .isPresent();
            inventory.setItem(
                    slot++,
                    button(
                            selected ? Material.GLOWSTONE_DUST : Material.BLAZE_POWDER,
                            (selected ? "Selected form: " : "Form: ")
                                    + form.id().value()
                                    + " — "
                                    + form.tradeoff(),
                            "form:" + form.id().value()));
        }
        inventory.setItem(37, button(Material.GRAY_DYE, "Clear active Form", "clear-form"));
        populateBuildCommitStatus(player, inventory, sceneSession, family);
    }

    private void populateCharacterInformationPage(
            Player player, Inventory inventory, SceneSession sceneSession) {
        LoadedCharacterSession character = characterSessions.active(player).orElse(null);
        if (character == null) {
            inventory.setItem(22, button(Material.BARRIER, "Character unavailable", "noop"));
            return;
        }
        inventory.setItem(
                20,
                button(
                        Material.PLAYER_HEAD,
                        "Character " + character.characterId().value().toString().substring(0, 8),
                        "noop"));
        inventory.setItem(
                22,
                button(
                        Material.IRON_SWORD,
                        "Weapon family: "
                                + previewWeaponFamily(player, sceneSession).orElse("UNARMED"),
                        "noop"));
        inventory.setItem(
                24,
                button(
                        Material.ENCHANTED_BOOK,
                        "Attunement capacity: "
                                + sceneSession.previewState().build().attunementCapacity(),
                        "noop"));
    }

    private void populateMagicAttunementPage(
            Player player, Inventory inventory, SceneSession sceneSession) {
        int slot = 10;
        for (AttunableEffectDefinition effect : buildEngine.attunableEffects()) {
            if (slot > 34) {
                break;
            }
            boolean selected =
                    sceneSession.previewState().build().attunedEffects().contains(effect.id());
            inventory.setItem(
                    slot++,
                    button(
                            selected ? Material.ENCHANTED_BOOK : Material.BOOK,
                            (selected ? "Attuned: " : "Available: ")
                                    + effect.id().value()
                                    + " ["
                                    + effect.attunementCost()
                                    + " capacity]",
                            "attune:" + effect.id().value()));
        }
        populateBuildCommitStatus(
                player,
                inventory,
                sceneSession,
                previewWeaponFamily(player, sceneSession).orElse(null));
    }

    private void populateBuildCommitStatus(
            Player player, Inventory inventory, SceneSession sceneSession, String family) {
        Result<BuildResolution, BuildErrorCode> resolved =
                resolveBuild(player, sceneSession.previewState().build(), family);
        if (resolved instanceof Result.Success<BuildResolution, BuildErrorCode> success) {
            BuildResolution value = success.value();
            inventory.setItem(
                    39,
                    button(
                            Material.AMETHYST_SHARD,
                            "Attunement "
                                    + value.attunementLoad()
                                    + "/"
                                    + value.build().attunementCapacity(),
                            "noop"));
        } else {
            Result.Failure<BuildResolution, BuildErrorCode> failure =
                    (Result.Failure<BuildResolution, BuildErrorCode>) resolved;
            inventory.setItem(
                    39,
                    button(
                            Material.BARRIER,
                            failure.error().code() + ": " + failure.detail(),
                            "noop"));
        }
        inventory.setItem(
                40,
                button(
                        !sceneSession.modeProfile().restContextRequiredForConfirm()
                                        || restContext(player)
                                ? Material.CAMPFIRE
                                : Material.REDSTONE_TORCH,
                        sceneSession.modeProfile().restContextRequiredForConfirm()
                                ? restContext(player)
                                        ? "Rest Context ready"
                                        : "Confirm requires Rest Context"
                                : "May confirm outside Rest Context",
                        "noop"));
        inventory.setItem(
                41,
                button(
                        sceneSession.hasUncommittedPreview()
                                ? Material.LIME_DYE
                                : Material.GRAY_DYE,
                        sceneSession.hasUncommittedPreview()
                                ? "Confirm prepared build"
                                : "No build change",
                        "confirm-build"));
    }

    private void previewTechnique(Player player, SceneInventoryHolder holder, String definitionId) {
        try {
            TechniqueDefinition technique =
                    buildEngine
                            .technique(DefinitionId.of(definitionId))
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Technique is unavailable."));
            SceneSession current = sessions.find(player.getUniqueId()).orElseThrow();
            DefinitionId selected =
                    current.previewState().build().techniques().get(technique.branch());
            CharacterBuild desired =
                    current.previewState()
                            .build()
                            .withTechnique(
                                    technique.branch(),
                                    technique.id().equals(selected)
                                            ? Optional.empty()
                                            : Optional.of(technique.id()));
            previewBuild(player, holder, desired);
        } catch (IllegalArgumentException | java.util.NoSuchElementException exception) {
            player.sendActionBar(Component.text(message(exception), NamedTextColor.RED));
        }
    }

    private void previewForm(Player player, SceneInventoryHolder holder, String definitionId) {
        try {
            Optional<DefinitionId> form =
                    definitionId == null
                            ? Optional.empty()
                            : Optional.of(
                                    buildEngine
                                            .form(DefinitionId.of(definitionId))
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalArgumentException(
                                                                    "Form is unavailable."))
                                            .id());
            SceneSession current = sessions.find(player.getUniqueId()).orElseThrow();
            previewBuild(player, holder, current.previewState().build().withForm(form));
        } catch (IllegalArgumentException | java.util.NoSuchElementException exception) {
            player.sendActionBar(Component.text(message(exception), NamedTextColor.RED));
        }
    }

    private void previewAttunement(
            Player player, SceneInventoryHolder holder, String definitionId) {
        try {
            DefinitionId effectId = DefinitionId.of(definitionId);
            if (buildEngine.attunableEffects().stream()
                    .noneMatch(effect -> effect.id().equals(effectId))) {
                throw new IllegalArgumentException("Attunable effect is unavailable.");
            }
            SceneSession current = sessions.find(player.getUniqueId()).orElseThrow();
            previewBuild(
                    player, holder, current.previewState().build().toggleAttunedEffect(effectId));
        } catch (IllegalArgumentException | java.util.NoSuchElementException exception) {
            player.sendActionBar(Component.text(message(exception), NamedTextColor.RED));
        }
    }

    private void previewBuild(Player player, SceneInventoryHolder holder, CharacterBuild desired) {
        SceneSession current = sessions.find(player.getUniqueId()).orElseThrow();
        String family = previewWeaponFamily(player, current).orElse(null);
        Result<BuildResolution, BuildErrorCode> validation = resolveBuild(player, desired, family);
        if (validation instanceof Result.Failure<BuildResolution, BuildErrorCode> failure) {
            throw new IllegalArgumentException(failure.error().code() + ": " + failure.detail());
        }
        Result<SceneSession, SceneErrorCode> result =
                sessions.previewBuild(player.getUniqueId(), holder.sessionId(), desired);
        result.map(
                session -> {
                    navigate(player, session);
                    return session;
                });
    }

    private void confirmBuild(Player player, SceneInventoryHolder holder) {
        if (!committing.add(player.getUniqueId())) {
            return;
        }
        SceneSession sceneSession = sessions.find(player.getUniqueId()).orElse(null);
        if (sceneSession == null
                || !sceneSession.sessionId().equals(holder.sessionId())
                || !sceneSession.hasUncommittedPreview()) {
            committing.remove(player.getUniqueId());
            return;
        }
        if (sceneSession.modeProfile().restContextRequiredForConfirm()) {
            try {
                requireRestContext(player);
            } catch (IllegalArgumentException exception) {
                committing.remove(player.getUniqueId());
                player.sendActionBar(Component.text(exception.getMessage(), NamedTextColor.RED));
                return;
            }
        }
        String family = previewWeaponFamily(player, sceneSession).orElse(null);
        Result<BuildResolution, BuildErrorCode> validation =
                resolveBuild(player, sceneSession.previewState().build(), family);
        if (validation instanceof Result.Failure<BuildResolution, BuildErrorCode> failure) {
            committing.remove(player.getUniqueId());
            player.sendActionBar(
                    Component.text(
                            failure.error().code() + ": " + failure.detail(), NamedTextColor.RED));
            return;
        }
        player.sendActionBar(Component.text("Committing prepared build...", NamedTextColor.YELLOW));
        characterSessions.commitBuild(
                player,
                sceneSession.previewState().build(),
                UUID.randomUUID(),
                contentVersion,
                result -> {
                    committing.remove(player.getUniqueId());
                    if (result
                            instanceof
                            Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>
                                    failure) {
                        player.sendActionBar(
                                Component.text(
                                        failure.error().code() + ": " + failure.detail(),
                                        NamedTextColor.RED));
                        return;
                    }
                    LoadedCharacterSession updated =
                            ((Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>)
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
                                                            updated.snapshot().quiverPreparation(),
                                                            updated.snapshot().build())));
                    if (confirmed instanceof Result.Success<SceneSession, SceneErrorCode> success) {
                        player.sendActionBar(
                                Component.text(
                                        sceneSession.modeProfile().restContextRequiredForConfirm()
                                                ? "Build committed at Rest Context."
                                                : "Build committed.",
                                        NamedTextColor.GREEN));
                        navigate(player, success.value());
                    }
                });
    }

    private Result<BuildResolution, BuildErrorCode> resolveBuild(
            Player player, CharacterBuild build, String family) {
        Set<KnowledgeKey> learned =
                characterSessions
                        .active(player)
                        .map(
                                session ->
                                        session.snapshot().learnedKnowledge().stream()
                                                .map(record -> record.knowledge())
                                                .collect(
                                                        java.util.stream.Collectors
                                                                .toUnmodifiableSet()))
                        .orElse(Set.of());
        return family == null
                ? buildEngine.resolve(build, learned)
                : buildEngine.resolve(build, family, learned);
    }

    private Optional<String> previewWeaponFamily(Player player, SceneSession sceneSession) {
        LoadedCharacterSession character = characterSessions.active(player).orElse(null);
        if (character == null) {
            return Optional.empty();
        }
        ItemDefinition main =
                equipmentDefinition(
                        character,
                        sceneSession.previewState().equipment(),
                        EquipmentSlot.MAIN_HAND);
        return main == null
                ? Optional.empty()
                : main.weaponProfile().map(WeaponCombatProfile::family);
    }

    private boolean restContext(Player player) {
        CombatSessionStatus status = combat.status(player).orElse(null);
        if (status == null
                || status.engagementState()
                        != com.branz.mmorpg.combat.state.EngagementState.EXPLORATION) {
            return false;
        }
        Location spawn = player.getWorld().getSpawnLocation();
        return player.getLocation().distanceSquared(spawn) <= restContextSpawnRadiusSquared;
    }

    private void requireRestContext(Player player) {
        if (!restContext(player)) {
            throw new IllegalArgumentException(
                    "Preparation requires Rest Context: return near world spawn outside combat.");
        }
    }

    private void populateFlaskPreparationPage(Player player, Inventory inventory) {
        LoadedCharacterSession character = characterSessions.active(player).orElse(null);
        if (character == null) {
            inventory.setItem(
                    22, button(Material.BARRIER, "Character session unavailable", "noop"));
            return;
        }
        FlaskAllocation allocation =
                flaskPreviews.computeIfAbsent(
                        player.getUniqueId(),
                        ignored ->
                                character.snapshot().expeditionState().flaskState().allocation());
        FlaskDose[] doses = FlaskDose.values();
        int[] rows = {11, 20, 29};
        for (int index = 0; index < doses.length; index++) {
            FlaskDose dose = doses[index];
            int center = rows[index];
            inventory.setItem(
                    center - 1,
                    button(
                            Material.RED_DYE,
                            "Move one slot away from " + doseLabel(dose),
                            "flask-adjust:" + dose.name() + ":-1"));
            inventory.setItem(
                    center,
                    button(
                            doseMaterial(dose),
                            doseLabel(dose) + " allocation: " + allocation.maximum(dose),
                            "noop"));
            inventory.setItem(
                    center + 1,
                    button(
                            Material.LIME_DYE,
                            "Move one slot to " + doseLabel(dose),
                            "flask-adjust:" + dose.name() + ":1"));
        }
        long stock = infusionStock(character);
        int charges = character.snapshot().expeditionState().flaskState().totalCharges();
        boolean mercyEligible = stock == 0 && charges < 2;
        inventory.setItem(
                38,
                button(
                        stock > 0 ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                        "Infusion Stock: " + stock,
                        "noop"));
        inventory.setItem(
                40,
                button(
                        restContext(player) ? Material.CAMPFIRE : Material.REDSTONE_TORCH,
                        restContext(player)
                                ? "Rest Context ready"
                                : "Return to spawn sanctuary outside combat",
                        "noop"));
        inventory.setItem(
                42,
                button(
                        mercyEligible ? Material.HONEY_BOTTLE : Material.GLASS_BOTTLE,
                        mercyEligible
                                ? "Mercy refill available: 2 charges"
                                : "Refill cost: up to " + allocation.capacity() + " Infusion Stock",
                        "noop"));
        inventory.setItem(
                49,
                button(
                        restContext(player) ? Material.LIME_DYE : Material.GRAY_DYE,
                        "Prepare and refill Flask",
                        "confirm-flask"));
    }

    private void adjustFlaskAllocation(
            Player player, SceneInventoryHolder holder, String adjustment) {
        try {
            requireRestContext(player);
            String[] parts = adjustment.split(":", 2);
            FlaskDose target = FlaskDose.valueOf(parts[0]);
            int direction = Integer.parseInt(parts[1]);
            if (direction != -1 && direction != 1) {
                return;
            }
            LoadedCharacterSession character = characterSessions.active(player).orElseThrow();
            FlaskAllocation current =
                    flaskPreviews.computeIfAbsent(
                            player.getUniqueId(),
                            ignored ->
                                    character
                                            .snapshot()
                                            .expeditionState()
                                            .flaskState()
                                            .allocation());
            EnumMap<FlaskDose, Integer> doses = new EnumMap<>(FlaskDose.class);
            doses.putAll(current.doses());
            FlaskDose counterpart = allocationCounterpart(doses, target, direction);
            if (counterpart == null) {
                player.sendActionBar(
                        Component.text("That allocation cannot move further.", NamedTextColor.RED));
                return;
            }
            doses.put(target, doses.get(target) + direction);
            doses.put(counterpart, doses.get(counterpart) - direction);
            flaskPreviews.put(player.getUniqueId(), new FlaskAllocation(current.capacity(), doses));
            sessions.find(player.getUniqueId())
                    .filter(scene -> scene.sessionId().equals(holder.sessionId()))
                    .ifPresent(scene -> navigate(player, scene));
        } catch (IllegalArgumentException | java.util.NoSuchElementException exception) {
            player.sendActionBar(Component.text(message(exception), NamedTextColor.RED));
        }
    }

    private static FlaskDose allocationCounterpart(
            Map<FlaskDose, Integer> doses, FlaskDose target, int direction) {
        for (int offset = 1; offset < FlaskDose.values().length; offset++) {
            FlaskDose candidate =
                    FlaskDose.values()[(target.ordinal() + offset) % FlaskDose.values().length];
            if (direction > 0 && doses.get(candidate) > 0) {
                return candidate;
            }
            if (direction < 0 && doses.get(target) > 0) {
                return candidate;
            }
        }
        return null;
    }

    private void confirmFlaskPreparation(Player player, SceneInventoryHolder holder) {
        if (!committing.add(player.getUniqueId())) {
            return;
        }
        SceneSession sceneSession = sessions.find(player.getUniqueId()).orElse(null);
        LoadedCharacterSession character = characterSessions.active(player).orElse(null);
        if (sceneSession == null
                || character == null
                || !sceneSession.sessionId().equals(holder.sessionId())) {
            committing.remove(player.getUniqueId());
            return;
        }
        try {
            requireRestContext(player);
        } catch (IllegalArgumentException exception) {
            committing.remove(player.getUniqueId());
            player.sendActionBar(Component.text(exception.getMessage(), NamedTextColor.RED));
            return;
        }
        FlaskAllocation allocation =
                flaskPreviews.getOrDefault(
                        player.getUniqueId(),
                        character.snapshot().expeditionState().flaskState().allocation());
        boolean mercyRequested =
                infusionStock(character) == 0
                        && character.snapshot().expeditionState().flaskState().totalCharges() < 2;
        player.sendActionBar(
                Component.text("Preparing Expedition Flask...", NamedTextColor.YELLOW));
        characterSessions.prepareFlaskAtRest(
                player,
                allocation,
                mercyRequested,
                UUID.randomUUID(),
                contentVersion,
                result -> {
                    committing.remove(player.getUniqueId());
                    if (result
                            instanceof
                            Result.Failure<FlaskPreparationCommitResult, CharacterSessionErrorCode>
                                    failure) {
                        player.sendActionBar(
                                Component.text(
                                        failure.error().code() + ": " + failure.detail(),
                                        NamedTextColor.RED));
                        return;
                    }
                    FlaskPreparationCommitResult committedResult =
                            ((Result.Success<
                                                    FlaskPreparationCommitResult,
                                                    CharacterSessionErrorCode>)
                                            result)
                                    .value();
                    flaskPreviews.put(
                            player.getUniqueId(),
                            committedResult.preparation().state().allocation());
                    String cost =
                            committedResult.preparation().mercyChargesGranted() > 0
                                    ? "Mercy refill granted "
                                            + committedResult.preparation().mercyChargesGranted()
                                            + " charges."
                                    : "Flask prepared using "
                                            + committedResult.preparation().infusionStockConsumed()
                                            + " Infusion Stock.";
                    player.sendActionBar(Component.text(cost, NamedTextColor.GREEN));
                    sessions.find(player.getUniqueId())
                            .filter(scene -> scene.sessionId().equals(holder.sessionId()))
                            .ifPresent(scene -> navigate(player, scene));
                });
    }

    private static long infusionStock(LoadedCharacterSession character) {
        DefinitionId stock = DefinitionId.of("material.infusion_stock");
        return character.snapshot().lotRecords().stream()
                .filter(lot -> lot.definitionId().equals(stock))
                .filter(lot -> lot.quantity() > 0)
                .filter(
                        lot ->
                                lot.ownerCharacterId()
                                        .filter(character.characterId()::equals)
                                        .isPresent())
                .filter(lot -> lot.location().type() == ValueLocationType.CHARACTER_INVENTORY)
                .mapToLong(LotLocationRecord::quantity)
                .sum();
    }

    private static Material doseMaterial(FlaskDose dose) {
        return switch (dose) {
            case HEALING -> Material.REDSTONE;
            case MANA -> Material.LAPIS_LAZULI;
            case STAMINA -> Material.SUGAR;
        };
    }

    private static String doseLabel(FlaskDose dose) {
        return switch (dose) {
            case HEALING -> "Healing";
            case MANA -> "Mana";
            case STAMINA -> "Stamina";
        };
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Build preview is stale or invalid."
                : exception.getMessage();
    }

    private void previewMainHand(Player player, SceneInventoryHolder holder, String itemUuid) {
        previewEquipment(player, holder, itemUuid, EquipmentSlot.MAIN_HAND);
    }

    private void previewOffHand(Player player, SceneInventoryHolder holder, String itemUuid) {
        previewEquipment(player, holder, itemUuid, EquipmentSlot.OFF_HAND);
    }

    private void previewQuiver(Player player, SceneInventoryHolder holder, String itemUuid) {
        previewEquipment(player, holder, itemUuid, EquipmentSlot.QUIVER);
    }

    private void previewEquipment(
            Player player, SceneInventoryHolder holder, String itemUuid, EquipmentSlot slot) {
        try {
            ItemId itemId = new ItemId(UUID.fromString(itemUuid));
            previewEquipment(player, holder, Optional.of(itemId), slot);
        } catch (IllegalArgumentException exception) {
            player.sendActionBar(
                    Component.text("Invalid equipment selection.", NamedTextColor.RED));
        }
    }

    private void previewEquipment(
            Player player,
            SceneInventoryHolder holder,
            Optional<ItemId> itemId,
            EquipmentSlot slot) {
        try {
            Result<SceneSession, SceneErrorCode> result =
                    sessions.previewEquipment(
                            player.getUniqueId(), holder.sessionId(), slot, itemId);
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
        if (equipmentChanged) {
            Optional<String> invalid =
                    equipmentCompatibilityFailure(player, sceneSession.previewState().equipment());
            if (invalid.isPresent()) {
                committing.remove(player.getUniqueId());
                player.sendActionBar(Component.text(invalid.orElseThrow(), NamedTextColor.RED));
                return;
            }
        }
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
                                                                            .quiverPreparation(),
                                                                    updated.snapshot().build())));
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

    private Optional<String> equipmentCompatibilityFailure(
            Player player, EquipmentLoadout loadout) {
        LoadedCharacterSession character = characterSessions.active(player).orElse(null);
        if (character == null) {
            return Optional.of("Character session is unavailable.");
        }
        ItemDefinition main = equipmentDefinition(character, loadout, EquipmentSlot.MAIN_HAND);
        ItemDefinition offHand = equipmentDefinition(character, loadout, EquipmentSlot.OFF_HAND);
        if (loadout.item(EquipmentSlot.MAIN_HAND).isPresent() && main == null) {
            return Optional.of("Main-hand item is missing from authoritative character state.");
        }
        if (loadout.item(EquipmentSlot.OFF_HAND).isPresent() && offHand == null) {
            return Optional.of("Off-hand item is missing from authoritative character state.");
        }
        if (main == null) {
            return offHand == null
                    ? Optional.empty()
                    : Optional.of("Unequip the Shield or select a Sword & Shield weapon.");
        }
        Result<WeaponLoadoutResolution, WeaponLoadoutErrorCode> resolved =
                WeaponLoadoutPolicy.resolve(main, Optional.ofNullable(offHand));
        return resolved
                        instanceof
                        Result.Failure<WeaponLoadoutResolution, WeaponLoadoutErrorCode> failure
                ? Optional.of(failure.detail())
                : Optional.empty();
    }

    private ItemDefinition equipmentDefinition(
            LoadedCharacterSession character, EquipmentLoadout loadout, EquipmentSlot slot) {
        ItemId itemId = loadout.item(slot).orElse(null);
        if (itemId == null) {
            return null;
        }
        return character.snapshot().itemRecords().stream()
                .filter(record -> record.itemId().equals(itemId))
                .findFirst()
                .flatMap(record -> itemEngine.find(record.definitionId()))
                .orElse(null);
    }

    private void updatePreviewActor(Mannequin actor, SceneSession sceneSession) {
        Player player = plugin.getServer().getPlayer(sceneSession.playerId());
        LoadedCharacterSession character =
                player == null ? null : characterSessions.active(player).orElse(null);
        if (player == null || character == null) {
            return;
        }
        org.bukkit.inventory.EntityEquipment equipment = actor.getEquipment();
        equipment.setArmorContents(player.getInventory().getArmorContents());
        equipment.setItemInMainHand(
                previewActorItem(player, character, sceneSession, EquipmentSlot.MAIN_HAND));
        equipment.setItemInOffHand(
                previewActorItem(player, character, sceneSession, EquipmentSlot.OFF_HAND));
    }

    private ItemStack previewActorItem(
            Player player,
            LoadedCharacterSession character,
            SceneSession sceneSession,
            EquipmentSlot slot) {
        ItemId desired = sceneSession.previewState().equipment().item(slot).orElse(null);
        if (desired == null) {
            return new ItemStack(Material.AIR);
        }
        ItemId committed = character.snapshot().equipment().item(slot).orElse(null);
        if (desired.equals(committed)) {
            ItemStack current =
                    slot == EquipmentSlot.MAIN_HAND
                            ? player.getInventory().getItem(0)
                            : player.getInventory().getItemInOffHand();
            return current == null ? new ItemStack(Material.AIR) : current.clone();
        }
        ItemDefinition definition =
                equipmentDefinition(character, sceneSession.previewState().equipment(), slot);
        if (definition == null) {
            return new ItemStack(Material.BARRIER);
        }
        ItemStack preview = new ItemStack(BukkitItemProjectionCodec.fallbackMaterial(definition));
        preview.editMeta(
                meta ->
                        meta.displayName(
                                Component.text(
                                        definition.id().value(), NamedTextColor.LIGHT_PURPLE)));
        return preview;
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
        SceneSession session = sessions.find(player.getUniqueId()).orElse(null);
        if (session == null || !recovering.add(player.getUniqueId())) {
            return;
        }
        RuntimeException providerFailure = null;
        try {
            try {
                sceneEngine.close(session.sessionId());
            } catch (RuntimeException exception) {
                providerFailure = exception;
            }
            closeState(player, reason);
            if (closeInventory
                    && player.getOpenInventory().getTopInventory().getHolder()
                            instanceof SceneInventoryHolder) {
                player.closeInventory();
            }
        } finally {
            recovering.remove(player.getUniqueId());
        }
        if (providerFailure != null) {
            plugin.getLogger()
                    .warning(
                            "Scene provider recovery failed for "
                                    + player.getUniqueId()
                                    + ": "
                                    + providerFailure);
        }
    }

    private void closeState(Player player, SceneCloseReason reason) {
        navigating.remove(player.getUniqueId());
        committing.remove(player.getUniqueId());
        flaskPreviews.remove(player.getUniqueId());
        sessions.closeCurrent(player.getUniqueId(), reason);
    }

    private static String modeTitle(SceneMode mode) {
        return switch (mode) {
            case HUB -> "Adventurer's Chronicle";
            case EQUIPMENT -> "Character & Equipment";
            case WARDROBE_DYE -> "Wardrobe & Dye";
            case COMBAT_ARTS -> "Combat Arts";
            case FORMS -> "Forms";
            case MAGIC_ATTUNEMENT -> "Magic & Attunement";
            case CHARACTER_INFORMATION -> "Character Information";
            case CHARACTER_CREATION -> "Character Creation";
            case APPEARANCE_PREVIEW -> "Appearance Preview";
            case IMPORTANT_DIALOGUE -> "Important Dialogue";
            case CUTSCENE -> "Cutscene";
            case FLASK_PREPARATION -> "Expedition Flask";
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
        buttons.put(SceneMode.FORMS, new ButtonSpec(28, Material.BLAZE_POWDER, "Forms"));
        buttons.put(
                SceneMode.JOURNAL_PENDING_REWARDS,
                new ButtonSpec(29, Material.CHEST, "Journal & Pending Rewards"));
        buttons.put(
                SceneMode.FLASK_PREPARATION,
                new ButtonSpec(31, Material.HONEY_BOTTLE, "Expedition Flask"));
        buttons.put(
                SceneMode.SETTINGS_HELP,
                new ButtonSpec(33, Material.COMPARATOR, "Settings & Help"));
        buttons.put(
                SceneMode.CHARACTER_INFORMATION,
                new ButtonSpec(34, Material.PLAYER_HEAD, "Character Information"));
        return Map.copyOf(buttons);
    }

    private record ButtonSpec(int slot, Material material, String label) {}

    private enum EligibilityAccepted {
        INSTANCE
    }
}
