package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.action.ActionPhase;
import com.branz.mmorpg.combat.action.ActionTimeline;
import com.branz.mmorpg.combat.action.ActionTimelineErrorCode;
import com.branz.mmorpg.combat.action.ActionTraceEvent;
import com.branz.mmorpg.combat.action.ActionTraceEventType;
import com.branz.mmorpg.combat.action.CombatResources;
import com.branz.mmorpg.combat.action.ResourceCommitState;
import com.branz.mmorpg.combat.damage.PhysicalDamageBreakdown;
import com.branz.mmorpg.combat.damage.PhysicalDamageRequest;
import com.branz.mmorpg.combat.damage.PhysicalDamageResolver;
import com.branz.mmorpg.combat.engagement.EngagementRuntime;
import com.branz.mmorpg.combat.engagement.EngagementTickContext;
import com.branz.mmorpg.combat.engagement.EngagementTracker;
import com.branz.mmorpg.combat.hitbox.ArcHitboxQuery;
import com.branz.mmorpg.combat.hitbox.ArcHitboxResolver;
import com.branz.mmorpg.combat.hitbox.CombatVector;
import com.branz.mmorpg.combat.hitbox.ResolvedTarget;
import com.branz.mmorpg.combat.hitbox.TargetCollider;
import com.branz.mmorpg.combat.input.ClientAction;
import com.branz.mmorpg.combat.input.CombatInputPolicy;
import com.branz.mmorpg.combat.input.CombatInputRequest;
import com.branz.mmorpg.combat.input.DirectionSnapshot;
import com.branz.mmorpg.combat.input.InputBufferClearReason;
import com.branz.mmorpg.combat.input.InputDeduplicationKey;
import com.branz.mmorpg.combat.input.InputObservation;
import com.branz.mmorpg.combat.input.InputPolicyContext;
import com.branz.mmorpg.combat.input.InputRejectionCode;
import com.branz.mmorpg.combat.input.InputRouteOutcome;
import com.branz.mmorpg.combat.input.InputRouter;
import com.branz.mmorpg.combat.input.InputRoutingContext;
import com.branz.mmorpg.combat.input.SemanticInput;
import com.branz.mmorpg.combat.move.MoveDefinition;
import com.branz.mmorpg.combat.move.MoveEngine;
import com.branz.mmorpg.combat.state.ActionState;
import com.branz.mmorpg.combat.state.UiState;
import com.branz.mmorpg.combat.state.WeaponState;
import com.branz.mmorpg.combat.weapon.SelectedHotbarSlot;
import com.branz.mmorpg.combat.weapon.SelectedSlotKind;
import com.branz.mmorpg.combat.weapon.WeaponTransitionErrorCode;
import com.branz.mmorpg.combat.weapon.WeaponTransitionMachine;
import com.branz.mmorpg.combat.weapon.WeaponTransitionSnapshot;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Main-thread Paper adapter for the deterministic training-move combat kernel. */
final class CombatSessionController implements Listener {
    private static final DefinitionId TRAINING_MOVE =
            DefinitionId.of("move.training_blade.primary_1");

    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private final MoveDefinition trainingMove;
    private final double trainingWeaponPower;
    private final WeaponTransitionMachine weapons;
    private final CombatInputPolicy inputPolicy = new CombatInputPolicy();
    private final ArcHitboxResolver hitboxes = new ArcHitboxResolver();
    private final PhysicalDamageResolver damage = new PhysicalDamageResolver();
    private final EngagementTracker engagement;
    private final Map<UUID, LiveSession> sessions = new HashMap<>();
    private final Map<UUID, Double> trainingTargetHealth = new HashMap<>();
    private int tickTaskId = -1;

    CombatSessionController(
            JavaPlugin plugin,
            CharacterSessionController characters,
            MoveEngine moves,
            double trainingWeaponPower,
            int drawTicks,
            int sheatheTicks,
            int engagementExitTicks) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        Objects.requireNonNull(moves, "moves");
        trainingMove =
                moves.find(TRAINING_MOVE)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "active content is missing " + TRAINING_MOVE));
        if (!Double.isFinite(trainingWeaponPower) || trainingWeaponPower <= 0) {
            throw new IllegalArgumentException("trainingWeaponPower must be positive");
        }
        this.trainingWeaponPower = trainingWeaponPower;
        weapons = new WeaponTransitionMachine(drawTicks, sheatheTicks);
        engagement = new EngagementTracker(engagementExitTicks);
    }

    void start() {
        tickTaskId =
                plugin.getServer()
                        .getScheduler()
                        .scheduleSyncRepeatingTask(plugin, this::tickAll, 1L, 1L);
    }

    void shutdown() {
        if (tickTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }
        sessions.clear();
    }

    void onCharacterReady(Player player) {
        LiveSession session =
                new LiveSession(EngagementRuntime.initial(plugin.getServer().getCurrentTick()));
        sessions.put(player.getUniqueId(), session);
        select(session, selectedSlot(player, player.getInventory().getHeldItemSlot()));
    }

    Optional<CombatSessionStatus> status(Player player) {
        LiveSession session = sessions.get(Objects.requireNonNull(player, "player").getUniqueId());
        if (session == null) {
            return Optional.empty();
        }
        CombatResources resources =
                session.timeline == null ? session.resources : session.timeline.resources();
        return Optional.of(
                new CombatSessionStatus(
                        session.engagement.state(),
                        engagement.remainingExitTicks(
                                session.engagement, plugin.getServer().getCurrentTick()),
                        session.weapon.state(),
                        Optional.ofNullable(session.timeline).map(ActionTimeline::phase),
                        resources.stamina(),
                        resources.reservedStamina(),
                        Optional.ofNullable(session.lastResolution)));
    }

    void startTrainingMove(Player player) {
        LiveSession session = sessions.get(player.getUniqueId());
        if (session == null || !characters.ready(player)) {
            player.sendActionBar(
                    Component.text("Combat session is not ready.", NamedTextColor.RED));
            return;
        }
        if (session.weapon.state() != WeaponState.READY) {
            player.sendActionBar(
                    Component.text(
                            "Equip the training blade in main hand and wait for READY.",
                            NamedTextColor.YELLOW));
            return;
        }
        startMove(player, session);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldSlot(PlayerItemHeldEvent event) {
        LiveSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        cancelAction(session, "WEAPON_SWAP");
        session.input.clearBuffer(InputBufferClearReason.WEAPON_SWAP);
        select(session, selectedSlot(event.getPlayer(), event.getNewSlot()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        LiveSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !characters.ready(event.getPlayer())) {
            return;
        }
        SemanticInput intent =
                resolvedIntent(event.getPlayer(), session, ClientAction.ATTACK).orElse(null);
        if (intent != SemanticInput.PRIMARY) {
            return;
        }
        event.setCancelled(true);
        Result<CombatInputRequest, InputRejectionCode> observed =
                session.input.observe(
                        new InputObservation(
                                plugin.getServer().getCurrentTick(),
                                intent,
                                DirectionSnapshot.NEUTRAL,
                                trainingMove.input().branch(),
                                new InputDeduplicationKey("MAIN_HAND", "ATTACK")));
        if (observed instanceof Result.Failure<CombatInputRequest, InputRejectionCode>) {
            return;
        }
        CombatInputRequest request =
                ((Result.Success<CombatInputRequest, InputRejectionCode>) observed).value();
        InputRoutingContext context = routingContext(event.getPlayer(), session);
        Result<InputRouteOutcome, InputRejectionCode> routed =
                session.input.routeFrame(List.of(request), context);
        if (routed instanceof Result.Success<InputRouteOutcome, InputRejectionCode> success) {
            handleRoute(event.getPlayer(), session, success.value());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaCombatDamage(EntityDamageByEntityEvent event) {
        if (event.getDamageSource().getCausingEntity() instanceof Player player) {
            LiveSession session = sessions.get(player.getUniqueId());
            if (session != null
                    && (session.weapon.state() == WeaponState.READY || session.timeline != null)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIncomingCombatDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !(event.getDamageSource().getCausingEntity() instanceof LivingEntity source)
                || source == player) {
            return;
        }
        LiveSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            markHostile(player, session);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob)) {
            return;
        }
        UUID threatOwner = event.getEntity().getUniqueId();
        sessions.values().forEach(session -> session.threatOwners.remove(threatOwner));
        if (!(event.getTarget() instanceof Player player)) {
            return;
        }
        LiveSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.threatOwners.add(threatOwner);
        session.engagement =
                engagement.alert(session.engagement, plugin.getServer().getCurrentTick());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        UUID threatOwner = event.getEntity().getUniqueId();
        sessions.values().forEach(session -> session.threatOwners.remove(threatOwner));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void tickAll() {
        for (Map.Entry<UUID, LiveSession> entry : sessions.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            LiveSession session = entry.getValue();
            WeaponState priorWeapon = session.weapon.state();
            session.weapon = weapons.tick(session.weapon);
            if (priorWeapon != WeaponState.READY && session.weapon.state() == WeaponState.READY) {
                player.sendActionBar(Component.text("Training blade READY", NamedTextColor.GREEN));
                pollBuffered(player, session);
            }
            tickAction(player, session);
            tickEngagement(player, session);
            regenerateStamina(session);
        }
    }

    private void tickAction(Player player, LiveSession session) {
        if (session.timeline == null) {
            return;
        }
        ActionPhase prior = session.timeline.phase();
        ResourceCommitState priorResourceState = session.timeline.resourceState();
        int priorTraceSize = session.timeline.trace().size();
        Result<ActionTimeline, ActionTimelineErrorCode> advanced = session.timeline.advance();
        if (advanced instanceof Result.Failure<ActionTimeline, ActionTimelineErrorCode>) {
            return;
        }
        session.timeline =
                ((Result.Success<ActionTimeline, ActionTimelineErrorCode>) advanced).value();
        if (priorResourceState == ResourceCommitState.RESERVED
                && session.timeline.resourceState() == ResourceCommitState.COMMITTED) {
            session.lastStaminaSpendTick = plugin.getServer().getCurrentTick();
            session.staminaRegenRemainder = 0;
            markHostile(player, session);
        }
        if (session.timeline.phase() != prior) {
            player.sendActionBar(
                    Component.text(
                            trainingMove.id().value()
                                    + " "
                                    + session.timeline.phase()
                                    + " tick="
                                    + session.timeline.tick()
                                    + " stamina="
                                    + session.timeline.resources().stamina(),
                            NamedTextColor.AQUA));
        }
        for (ActionTraceEvent event :
                session.timeline.trace().subList(priorTraceSize, session.timeline.trace().size())) {
            if (event.type() == ActionTraceEventType.HITBOX_OPENED) {
                resolveTrainingHitbox(player, session);
            }
        }
        if (session.timeline.phase().terminal()) {
            session.resources = session.timeline.resources();
            session.timeline = null;
            session.action = ActionState.IDLE;
        } else {
            session.action = actionState(session.timeline.phase());
        }
    }

    private void resolveTrainingHitbox(Player player, LiveSession session) {
        MoveDefinition.Hitbox hitbox = trainingMove.hitboxes().getFirst();
        if (hitbox.shape() != MoveDefinition.HitboxShape.ARC) {
            session.lastResolution = "unsupported " + hitbox.shape();
            return;
        }
        org.bukkit.Location origin = player.getLocation();
        org.bukkit.util.Vector direction = origin.getDirection();
        ArcHitboxQuery query =
                new ArcHitboxQuery(
                        new CombatVector(origin.getX(), origin.getY(), origin.getZ()),
                        new CombatVector(direction.getX(), 0, direction.getZ()),
                        hitbox.range(),
                        hitbox.angleDegrees(),
                        -0.5,
                        hitbox.height(),
                        hitbox.maxTargets());
        Map<UUID, LivingEntity> entities = new HashMap<>();
        List<TargetCollider> candidates =
                player
                        .getWorld()
                        .getNearbyEntities(
                                origin, hitbox.range() + 1, hitbox.height() + 1, hitbox.range() + 1)
                        .stream()
                        .filter(LivingEntity.class::isInstance)
                        .map(LivingEntity.class::cast)
                        .filter(entity -> entity != player)
                        .peek(entity -> entities.put(entity.getUniqueId(), entity))
                        .map(
                                entity ->
                                        new TargetCollider(
                                                entity.getUniqueId(),
                                                new CombatVector(
                                                        entity.getLocation().getX(),
                                                        entity.getLocation().getY(),
                                                        entity.getLocation().getZ()),
                                                Math.max(
                                                                entity.getBoundingBox().getWidthX(),
                                                                entity.getBoundingBox().getWidthZ())
                                                        / 2.0,
                                                entity.getBoundingBox().getHeight(),
                                                !(entity instanceof Player)
                                                        && !(entity instanceof ArmorStand)
                                                        && !entity.isDead(),
                                                player.hasLineOfSight(entity),
                                                false))
                        .toList();
        List<ResolvedTarget> resolved = hitboxes.resolve(query, candidates);
        if (resolved.isEmpty()) {
            session.lastResolution = "MISS tick=" + session.timeline.tick();
            player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.GRAY));
            return;
        }
        double totalDamage = 0;
        for (ResolvedTarget target : resolved) {
            LivingEntity entity = entities.get(target.entityId());
            double armor =
                    entity.getAttribute(Attribute.ARMOR) == null
                            ? 0
                            : Objects.requireNonNull(entity.getAttribute(Attribute.ARMOR))
                                    .getValue();
            PhysicalDamageBreakdown breakdown =
                    damage.resolve(
                            new PhysicalDamageRequest(
                                    trainingWeaponPower,
                                    trainingMove.outputs().moveCoefficient(),
                                    0,
                                    armor,
                                    0,
                                    0,
                                    0,
                                    java.util.Set.of(),
                                    trainingMove.profiles().pveMultiplier()));
            totalDamage += breakdown.finalDamage();
            trainingTargetHealth.compute(
                    target.entityId(),
                    (ignored, current) ->
                            Math.max(
                                    0,
                                    (current == null ? 1000.0 : current)
                                            - breakdown.finalDamage()));
        }
        session.lastResolution =
                "HIT targets="
                        + resolved.size()
                        + " damage="
                        + Math.round(totalDamage * 10.0) / 10.0;
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.GREEN));
    }

    private void regenerateStamina(LiveSession session) {
        if (session.timeline != null
                || session.resources.stamina() >= session.resources.maximumStamina()) {
            return;
        }
        long currentTick = plugin.getServer().getCurrentTick();
        if (currentTick - session.lastStaminaSpendTick < 20) {
            return;
        }
        session.staminaRegenRemainder += 12.0 / 20.0;
        int whole = (int) session.staminaRegenRemainder;
        if (whole > 0) {
            session.resources = session.resources.restoreStamina(whole);
            session.staminaRegenRemainder -= whole;
        }
    }

    private void tickEngagement(Player player, LiveSession session) {
        session.threatOwners.removeIf(
                entityId -> {
                    Entity entity = plugin.getServer().getEntity(entityId);
                    return !(entity instanceof Mob mob)
                            || !entity.isValid()
                            || !player.equals(mob.getTarget());
                });
        session.engagement =
                engagement.tick(
                        session.engagement,
                        plugin.getServer().getCurrentTick(),
                        new EngagementTickContext(
                                !session.threatOwners.isEmpty(),
                                false,
                                session.action == ActionState.DOWNED
                                        || session.action == ActionState.DEAD));
    }

    private void markHostile(Player player, LiveSession session) {
        session.engagement =
                engagement.hostileActivity(session.engagement, plugin.getServer().getCurrentTick());
        if (player.getOpenInventory().getTopInventory().getHolder()
                instanceof SceneInventoryHolder) {
            player.closeInventory();
        }
    }

    private void pollBuffered(Player player, LiveSession session) {
        Result<InputRouteOutcome, InputRejectionCode> result =
                session.input.pollBuffered(
                        plugin.getServer().getCurrentTick(), routingContext(player, session));
        if (result instanceof Result.Success<InputRouteOutcome, InputRejectionCode> success) {
            handleRoute(player, session, success.value());
        }
    }

    private void handleRoute(Player player, LiveSession session, InputRouteOutcome outcome) {
        if (outcome.decision() == com.branz.mmorpg.combat.input.InputRouteDecision.EXECUTED) {
            startMove(player, session);
        } else {
            player.sendActionBar(Component.text("Primary opener buffered.", NamedTextColor.YELLOW));
        }
    }

    private void startMove(Player player, LiveSession session) {
        if (session.timeline != null) {
            return;
        }
        Result<ActionTimeline, ActionTimelineErrorCode> started =
                ActionTimeline.start(trainingMove, session.resources);
        if (started instanceof Result.Failure<ActionTimeline, ActionTimelineErrorCode> failure) {
            player.sendActionBar(
                    Component.text("Move rejected: " + failure.error().code(), NamedTextColor.RED));
            return;
        }
        session.timeline =
                ((Result.Success<ActionTimeline, ActionTimelineErrorCode>) started).value();
        session.action = actionState(session.timeline.phase());
        player.sendActionBar(
                Component.text(
                        trainingMove.id().value()
                                + " started; stamina reserved="
                                + trainingMove.costs().stamina(),
                        NamedTextColor.AQUA));
    }

    private void cancelAction(LiveSession session, String reason) {
        if (session.timeline == null) {
            return;
        }
        Result<ActionTimeline, ActionTimelineErrorCode> cancelled = session.timeline.cancel(reason);
        if (cancelled instanceof Result.Success<ActionTimeline, ActionTimelineErrorCode> success) {
            session.resources = success.value().resources();
        }
        session.timeline = null;
        session.action = ActionState.IDLE;
    }

    private Optional<SemanticInput> resolvedIntent(
            Player player, LiveSession session, ClientAction action) {
        Result<SemanticInput, InputRejectionCode> resolved =
                inputPolicy.resolve(action, policyContext(player, session));
        return resolved instanceof Result.Success<SemanticInput, InputRejectionCode> success
                ? Optional.of(success.value())
                : Optional.empty();
    }

    private InputRoutingContext routingContext(Player player, LiveSession session) {
        return inputPolicy.routingContext(policyContext(player, session), false);
    }

    private InputPolicyContext policyContext(Player player, LiveSession session) {
        UiState ui =
                player.getOpenInventory().getTopInventory().getHolder()
                                instanceof SceneInventoryHolder
                        ? UiState.SCENE
                        : UiState.NONE;
        return new InputPolicyContext(
                session.engagement.state(),
                session.weapon.state(),
                session.action,
                ui,
                false,
                DirectionSnapshot.NEUTRAL);
    }

    private SelectedHotbarSlot selectedSlot(Player player, int slot) {
        if (slot == ChronicleService.HOTBAR_SLOT) {
            return new SelectedHotbarSlot(slot, SelectedSlotKind.CHRONICLE);
        }
        boolean mainHandEquipped =
                characters
                        .active(player)
                        .flatMap(
                                session ->
                                        session.snapshot()
                                                .equipment()
                                                .item(EquipmentSlot.MAIN_HAND))
                        .isPresent();
        return mainHandEquipped && slot == 0
                ? SelectedHotbarSlot.combatWeapon(slot)
                : new SelectedHotbarSlot(slot, SelectedSlotKind.TOOL_OR_BLOCK);
    }

    private void select(LiveSession session, SelectedHotbarSlot selected) {
        Result<WeaponTransitionSnapshot, WeaponTransitionErrorCode> result =
                weapons.select(session.weapon, selected);
        if (result
                instanceof
                Result.Success<WeaponTransitionSnapshot, WeaponTransitionErrorCode> success) {
            session.weapon = success.value();
        }
    }

    private static ActionState actionState(ActionPhase phase) {
        return switch (phase) {
            case WINDUP -> ActionState.WINDUP;
            case ACTIVE -> ActionState.ACTIVE;
            case RECOVERY -> ActionState.RECOVERY;
            case COMPLETE, CANCELLED -> ActionState.IDLE;
        };
    }

    private static final class LiveSession {
        private EngagementRuntime engagement;
        private WeaponTransitionSnapshot weapon = WeaponTransitionSnapshot.initial();
        private ActionState action = ActionState.IDLE;
        private CombatResources resources = CombatResources.full(1000, 100, 100);
        private final InputRouter input = new InputRouter();
        private ActionTimeline timeline;
        private long lastStaminaSpendTick = Long.MIN_VALUE / 2;
        private double staminaRegenRemainder;
        private String lastResolution;
        private final java.util.Set<UUID> threatOwners = new java.util.HashSet<>();

        private LiveSession(EngagementRuntime engagement) {
            this.engagement = Objects.requireNonNull(engagement, "engagement");
        }
    }
}
