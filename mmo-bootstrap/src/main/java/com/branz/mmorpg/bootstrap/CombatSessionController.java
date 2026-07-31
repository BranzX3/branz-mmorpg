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
import com.branz.mmorpg.combat.cc.CcApplication;
import com.branz.mmorpg.combat.cc.CcEngine;
import com.branz.mmorpg.combat.cc.CcRequest;
import com.branz.mmorpg.combat.cc.CcRuntime;
import com.branz.mmorpg.combat.cc.CcSeverity;
import com.branz.mmorpg.combat.damage.ConditionalAdvantage;
import com.branz.mmorpg.combat.damage.PhysicalDamageBreakdown;
import com.branz.mmorpg.combat.damage.PhysicalDamageRequest;
import com.branz.mmorpg.combat.damage.PhysicalDamageResolver;
import com.branz.mmorpg.combat.dodge.DodgeEngine;
import com.branz.mmorpg.combat.dodge.DodgeErrorCode;
import com.branz.mmorpg.combat.dodge.DodgePhase;
import com.branz.mmorpg.combat.dodge.DodgeProfile;
import com.branz.mmorpg.combat.dodge.DodgeRuntime;
import com.branz.mmorpg.combat.engagement.EngagementRuntime;
import com.branz.mmorpg.combat.engagement.EngagementTickContext;
import com.branz.mmorpg.combat.engagement.EngagementTracker;
import com.branz.mmorpg.combat.guard.CombatDefenseOutcome;
import com.branz.mmorpg.combat.guard.CombatDefenseResolution;
import com.branz.mmorpg.combat.guard.CombatDefenseResolver;
import com.branz.mmorpg.combat.guard.GuardEngine;
import com.branz.mmorpg.combat.guard.GuardErrorCode;
import com.branz.mmorpg.combat.guard.GuardHitRequest;
import com.branz.mmorpg.combat.guard.GuardRuntime;
import com.branz.mmorpg.combat.hitbox.ArcDebugGeometry;
import com.branz.mmorpg.combat.hitbox.ArcHitboxQuery;
import com.branz.mmorpg.combat.hitbox.CombatVector;
import com.branz.mmorpg.combat.hitbox.ResolvedTarget;
import com.branz.mmorpg.combat.hitbox.SweptArcHitboxQuery;
import com.branz.mmorpg.combat.hitbox.SweptArcHitboxResolver;
import com.branz.mmorpg.combat.hitbox.SweptArcResolution;
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
import com.branz.mmorpg.combat.input.SneakPressDecision;
import com.branz.mmorpg.combat.input.SneakPressResolver;
import com.branz.mmorpg.combat.input.SneakPressWindow;
import com.branz.mmorpg.combat.move.MoveDefinition;
import com.branz.mmorpg.combat.move.MoveEngine;
import com.branz.mmorpg.combat.poise.PoiseEngine;
import com.branz.mmorpg.combat.poise.PoiseProfile;
import com.branz.mmorpg.combat.poise.PoiseResolution;
import com.branz.mmorpg.combat.poise.PoiseRuntime;
import com.branz.mmorpg.combat.posture.PostureEngine;
import com.branz.mmorpg.combat.posture.PosturePhase;
import com.branz.mmorpg.combat.posture.PostureProfile;
import com.branz.mmorpg.combat.posture.PostureResolution;
import com.branz.mmorpg.combat.posture.PostureRuntime;
import com.branz.mmorpg.combat.state.ActionState;
import com.branz.mmorpg.combat.state.EngagementState;
import com.branz.mmorpg.combat.state.UiState;
import com.branz.mmorpg.combat.state.WeaponState;
import com.branz.mmorpg.combat.trace.ActionSimulationCommand;
import com.branz.mmorpg.combat.trace.ActionTimelineSimulator;
import com.branz.mmorpg.combat.trace.CombatSimulationErrorCode;
import com.branz.mmorpg.combat.trace.CombatTrace;
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
import org.bukkit.FluidCollisionMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Main-thread Paper adapter for the deterministic training-move combat kernel. */
final class CombatSessionController implements Listener {
    private static final DefinitionId TRAINING_MOVE =
            DefinitionId.of("move.training_blade.primary_1");

    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private final MoveEngine moves;
    private final String contentVersion;
    private final MoveDefinition trainingMove;
    private final double trainingWeaponPower;
    private final WeaponTransitionMachine weapons;
    private final CombatInputPolicy inputPolicy = new CombatInputPolicy();
    private final SweptArcHitboxResolver hitboxes = new SweptArcHitboxResolver();
    private final ArcDebugGeometry arcDebugGeometry = new ArcDebugGeometry();
    private final PhysicalDamageResolver damage = new PhysicalDamageResolver();
    private final EngagementTracker engagement;
    private final DodgeProfile dodgeProfile;
    private final DodgeEngine dodges = new DodgeEngine();
    private final SneakPressResolver sneakPresses = new SneakPressResolver();
    private final GuardEngine guards;
    private final CombatDefenseResolver defense;
    private final double trainingIncomingGuardPressure;
    private final double trainingIncomingPoiseDamage;
    private final CcSeverity trainingIncomingCcSeverity;
    private final int trainingIncomingCcTicks;
    private final double trainingPerfectGuardPostureDamage;
    private final PostureEngine postures = new PostureEngine(PostureProfile.trainingNormal());
    private final PoiseEngine poise = new PoiseEngine(PoiseProfile.trainingPlayer());
    private final CcEngine crowdControl = new CcEngine();
    private final Map<UUID, LiveSession> sessions = new HashMap<>();
    private final Map<UUID, Double> trainingTargetHealth = new HashMap<>();
    private final Map<UUID, PostureRuntime> trainingTargetPosture = new HashMap<>();
    private final Map<UUID, java.util.Set<UUID>> debugViewers = new HashMap<>();
    private int tickTaskId = -1;

    CombatSessionController(
            JavaPlugin plugin,
            CharacterSessionController characters,
            MoveEngine moves,
            String contentVersion,
            double trainingWeaponPower,
            int drawTicks,
            int sheatheTicks,
            int engagementExitTicks,
            DodgeProfile dodgeProfile,
            GuardEngine guards,
            double trainingIncomingGuardPressure,
            double trainingIncomingPoiseDamage,
            CcSeverity trainingIncomingCcSeverity,
            int trainingIncomingCcTicks,
            double trainingPerfectGuardPostureDamage) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.moves = Objects.requireNonNull(moves, "moves");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        if (contentVersion.isBlank()) {
            throw new IllegalArgumentException("contentVersion must not be blank");
        }
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
        this.dodgeProfile = Objects.requireNonNull(dodgeProfile, "dodgeProfile");
        this.guards = Objects.requireNonNull(guards, "guards");
        defense = new CombatDefenseResolver(dodges, guards);
        if (!Double.isFinite(trainingIncomingGuardPressure) || trainingIncomingGuardPressure <= 0) {
            throw new IllegalArgumentException("trainingIncomingGuardPressure must be positive");
        }
        this.trainingIncomingGuardPressure = trainingIncomingGuardPressure;
        if (!Double.isFinite(trainingIncomingPoiseDamage) || trainingIncomingPoiseDamage <= 0) {
            throw new IllegalArgumentException("trainingIncomingPoiseDamage must be positive");
        }
        this.trainingIncomingPoiseDamage = trainingIncomingPoiseDamage;
        this.trainingIncomingCcSeverity =
                Objects.requireNonNull(trainingIncomingCcSeverity, "trainingIncomingCcSeverity");
        if (trainingIncomingCcTicks < 1) {
            throw new IllegalArgumentException("trainingIncomingCcTicks must be positive");
        }
        this.trainingIncomingCcTicks = trainingIncomingCcTicks;
        if (!Double.isFinite(trainingPerfectGuardPostureDamage)
                || trainingPerfectGuardPostureDamage <= 0) {
            throw new IllegalArgumentException(
                    "trainingPerfectGuardPostureDamage must be positive");
        }
        this.trainingPerfectGuardPostureDamage = trainingPerfectGuardPostureDamage;
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
        trainingTargetHealth.clear();
        trainingTargetPosture.clear();
        debugViewers.clear();
    }

    void onCharacterReady(Player player) {
        long tick = plugin.getServer().getCurrentTick();
        LiveSession session =
                new LiveSession(
                        EngagementRuntime.initial(tick),
                        GuardRuntime.initial(guards.profile(), tick),
                        PoiseRuntime.initial(tick),
                        CcRuntime.initial(tick));
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
                        dodgeProfile.load(),
                        Optional.ofNullable(session.dodge)
                                .map(
                                        runtime ->
                                                runtime.phaseAt(
                                                        plugin.getServer().getCurrentTick())),
                        guards.phaseAt(session.guard, plugin.getServer().getCurrentTick()),
                        session.guard.stability(),
                        session.crowdControl.active(),
                        session.crowdControl
                                .active()
                                .map(
                                        ignored ->
                                                (int)
                                                        Math.max(
                                                                0,
                                                                session.crowdControl
                                                                                .activeUntilTick()
                                                                        - plugin.getServer()
                                                                                .getCurrentTick()))
                                .orElse(0),
                        resources.stamina(),
                        resources.reservedStamina(),
                        Optional.ofNullable(session.lastResolution)));
    }

    Optional<Boolean> toggleDebug(Player viewer, Player target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        if (!sessions.containsKey(target.getUniqueId())) {
            return Optional.empty();
        }
        java.util.Set<UUID> viewers =
                debugViewers.computeIfAbsent(
                        target.getUniqueId(), ignored -> new java.util.HashSet<>());
        boolean enabled;
        if (viewers.remove(viewer.getUniqueId())) {
            enabled = false;
        } else {
            viewers.add(viewer.getUniqueId());
            enabled = true;
        }
        if (viewers.isEmpty()) {
            debugViewers.remove(target.getUniqueId());
        }
        return Optional.of(enabled);
    }

    Optional<CombatTrace> latestTrace(Player target) {
        Objects.requireNonNull(target, "target");
        LiveSession session = sessions.get(target.getUniqueId());
        return session == null ? Optional.empty() : Optional.ofNullable(session.lastTrace);
    }

    Result<CombatTrace, CombatSimulationErrorCode> replayTrace(CombatTrace trace) {
        return new ActionTimelineSimulator().replay(Objects.requireNonNull(trace, "trace"), moves);
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
        releaseGuard(session);
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
        if (event.getDamageSource().getCausingEntity() instanceof Player attacker) {
            LiveSession attackerSession = sessions.get(attacker.getUniqueId());
            if (attackerSession != null
                    && (attackerSession.weapon.state() == WeaponState.READY
                            || attackerSession.timeline != null)) {
                event.setCancelled(true);
                return;
            }
        }
        if (!(event.getEntity() instanceof Player defender)
                || !(event.getDamageSource().getCausingEntity() instanceof LivingEntity source)) {
            return;
        }
        LiveSession defenderSession = sessions.get(defender.getUniqueId());
        if (defenderSession == null) {
            return;
        }
        CombatDefenseResolution resolved =
                defense.resolve(
                        Optional.ofNullable(defenderSession.dodge),
                        defenderSession.guard,
                        plugin.getServer().getCurrentTick(),
                        true,
                        guardHitRequest(defender, defenderSession, source, event.getDamage()));
        long currentTick = plugin.getServer().getCurrentTick();
        defenderSession.lastDefenseTick = currentTick;
        defenderSession.lastDefenseOutcome = resolved.outcome();
        if (!resolved.defended()) {
            return;
        }
        defenderSession.guard = resolved.guardRuntime();
        if (resolved.staminaSpent() > 0) {
            defenderSession.resources =
                    defenderSession.resources.spendStamina(resolved.staminaSpent()).orElseThrow();
            defenderSession.lastStaminaSpendTick = plugin.getServer().getCurrentTick();
            defenderSession.staminaRegenRemainder = 0;
        }
        defenderSession.lastResolution =
                resolved.outcome()
                        + " damage="
                        + roundOne(resolved.finalDamage())
                        + " stability="
                        + roundOne(resolved.guardRuntime().stability());
        NamedTextColor feedbackColor =
                switch (resolved.outcome()) {
                    case DODGED -> NamedTextColor.AQUA;
                    case PERFECT_GUARD -> NamedTextColor.GOLD;
                    case GUARDED, GUARD_BREAK -> NamedTextColor.YELLOW;
                    case HIT -> NamedTextColor.RED;
                };
        defender.sendActionBar(Component.text(defenderSession.lastResolution, feedbackColor));
        if (resolved.outcome() == CombatDefenseOutcome.DODGED
                || resolved.outcome() == CombatDefenseOutcome.PERFECT_GUARD) {
            event.setCancelled(true);
        } else {
            event.setDamage(resolved.finalDamage());
        }
        if (resolved.outcome() != CombatDefenseOutcome.DODGED) {
            markHostile(defender, defenderSession);
        }
        if (resolved.outcome() == CombatDefenseOutcome.PERFECT_GUARD
                && !(source instanceof Player)) {
            PostureResolution posture =
                    applyPostureDamage(
                            source.getUniqueId(), currentTick, trainingPerfectGuardPostureDamage);
            defenderSession.lastResolution =
                    "PERFECT_GUARD attacker-posture="
                            + postureLabel(posture.runtime(), currentTick);
            defender.sendActionBar(
                    Component.text(defenderSession.lastResolution, NamedTextColor.GOLD));
        } else if (resolved.outcome() == CombatDefenseOutcome.GUARD_BREAK) {
            applyCc(
                    defender,
                    defenderSession,
                    CcSeverity.HEAVY_STAGGER,
                    24,
                    source instanceof Player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuardUse(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                        && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        LiveSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null
                || !characters.ready(event.getPlayer())
                || session.weapon.state() != WeaponState.READY
                || session.engagement.state() != EngagementState.ENGAGED) {
            return;
        }
        Result<SemanticInput, InputRejectionCode> semantic =
                inputPolicy.resolve(ClientAction.USE, policyContext(event.getPlayer(), session));
        if (!(semantic instanceof Result.Success<SemanticInput, InputRejectionCode> success)
                || success.value() != SemanticInput.SECONDARY) {
            return;
        }
        event.setCancelled(true);
        toggleGuard(event.getPlayer(), session);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSneak(PlayerToggleSneakEvent event) {
        LiveSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !characters.ready(event.getPlayer())) {
            return;
        }
        if (!event.isSneaking()) {
            session.sneakPress = null;
            return;
        }
        if (!combatDodgeContext(session)) {
            return;
        }
        long tick = plugin.getServer().getCurrentTick();
        DirectionSnapshot direction = direction(event.getPlayer().getCurrentInput());
        session.sneakPress = new SneakPressWindow(tick);
        if (direction != DirectionSnapshot.NEUTRAL) {
            event.setCancelled(true);
            session.sneakPress = null;
            requestDodge(event.getPlayer(), session, direction);
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
            long tick = plugin.getServer().getCurrentTick();
            if (session.lastDefenseTick == tick
                    && session.lastDefenseOutcome != CombatDefenseOutcome.HIT) {
                return;
            }
            PoiseResolution poiseResolution =
                    poise.apply(
                            session.poise,
                            tick,
                            trainingIncomingPoiseDamage,
                            1.0,
                            trainingIncomingCcSeverity);
            session.poise = poiseResolution.runtime();
            poiseResolution
                    .triggeredSeverity()
                    .ifPresent(
                            severity ->
                                    applyCc(
                                            player,
                                            session,
                                            severity,
                                            trainingIncomingCcTicks,
                                            source instanceof Player));
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
        trainingTargetHealth.remove(threatOwner);
        trainingTargetPosture.remove(threatOwner);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        sessions.remove(playerId);
        debugViewers.remove(playerId);
        debugViewers.values().forEach(viewers -> viewers.remove(playerId));
        debugViewers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        LiveSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        boolean sameWorld = event.getFrom().getWorld() == event.getTo().getWorld();
        boolean authoritativeDodgeStep = sameWorld && session.applyingDodgeTeleport;
        if (authoritativeDodgeStep) {
            return;
        }
        cancelAction(session, sameWorld ? "FORCED_TELEPORT" : "WORLD_CHANGE");
        session.input.clearBuffer(InputBufferClearReason.WORLD_CHANGE);
        releaseGuard(session);
        session.dodge = null;
        session.dodgeDirection = null;
        session.sneakPress = null;
        session.previousActionTransform = null;
        session.weapon = weapons.resetTransient();
        session.action = ActionState.IDLE;
        select(
                session,
                selectedSlot(
                        event.getPlayer(), event.getPlayer().getInventory().getHeldItemSlot()));
    }

    private void tickAll() {
        tickTrainingPosture();
        for (Map.Entry<UUID, LiveSession> entry : sessions.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            LiveSession session = entry.getValue();
            tickCrowdControl(session);
            WeaponState priorWeapon = session.weapon.state();
            session.weapon = weapons.tick(session.weapon);
            if (priorWeapon != WeaponState.READY && session.weapon.state() == WeaponState.READY) {
                player.sendActionBar(Component.text("Training blade READY", NamedTextColor.GREEN));
                pollBuffered(player, session);
            }
            tickSneakPress(player, session);
            tickDodge(player, session);
            session.guard = guards.tick(session.guard, plugin.getServer().getCurrentTick());
            session.poise = poise.tick(session.poise, plugin.getServer().getCurrentTick());
            tickAction(player, session);
            tickEngagement(player, session);
            regenerateStamina(session);
        }
    }

    private void tickAction(Player player, LiveSession session) {
        if (session.timeline == null) {
            return;
        }
        CombatTransform currentTransform = combatTransform(player);
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
                resolveTrainingHitbox(player, session, currentTransform);
            }
        }
        session.previousActionTransform = currentTransform;
        if (session.timeline.phase().terminal()) {
            session.resources = session.timeline.resources();
            finishTrace(session, session.timeline);
            session.timeline = null;
            session.previousActionTransform = null;
            session.action = ActionState.IDLE;
        } else {
            session.action = actionState(session.timeline.phase());
        }
    }

    private void resolveTrainingHitbox(
            Player player, LiveSession session, CombatTransform currentTransform) {
        MoveDefinition.Hitbox hitbox = trainingMove.hitboxes().getFirst();
        if (hitbox.shape() != MoveDefinition.HitboxShape.ARC) {
            session.lastResolution = "unsupported " + hitbox.shape();
            return;
        }
        CombatTransform previousTransform =
                session.previousActionTransform == null
                                || !session.previousActionTransform
                                        .worldId()
                                        .equals(currentTransform.worldId())
                        ? currentTransform
                        : session.previousActionTransform;
        ArcHitboxQuery previousQuery = arcQuery(hitbox, previousTransform);
        ArcHitboxQuery currentQuery = arcQuery(hitbox, currentTransform);
        CombatVector midpoint = midpoint(previousTransform.origin(), currentTransform.origin());
        double xRadius =
                hitbox.range()
                        + Math.abs(currentTransform.origin().x() - previousTransform.origin().x())
                                / 2.0
                        + 1;
        double yRadius =
                hitbox.height()
                        + Math.abs(currentTransform.origin().y() - previousTransform.origin().y())
                                / 2.0
                        + 1;
        double zRadius =
                hitbox.range()
                        + Math.abs(currentTransform.origin().z() - previousTransform.origin().z())
                                / 2.0
                        + 1;
        Map<UUID, LivingEntity> entities = new HashMap<>();
        List<TargetCollider> candidates =
                player
                        .getWorld()
                        .getNearbyEntities(
                                new Location(
                                        player.getWorld(),
                                        midpoint.x(),
                                        midpoint.y(),
                                        midpoint.z()),
                                xRadius,
                                yRadius,
                                zRadius)
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
        SweptArcResolution sweep =
                hitboxes.resolve(new SweptArcHitboxQuery(previousQuery, currentQuery), candidates);
        List<ResolvedTarget> resolved = sweep.targets();
        renderArcDebug(
                player,
                previousQuery,
                currentQuery,
                sweep.sampledOrigins(),
                candidates,
                resolved,
                entities);
        if (sweep.samplingCapped()) {
            session.lastResolution = "SWEEP_REJECTED motion exceeds 16 blocks/tick";
            player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
            return;
        }
        if (resolved.isEmpty()) {
            session.lastResolution = "MISS tick=" + session.timeline.tick();
            player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.GRAY));
            return;
        }
        double totalDamage = 0;
        int postureBreaks = 0;
        String firstPosture = null;
        long currentTick = plugin.getServer().getCurrentTick();
        for (ResolvedTarget target : resolved) {
            LivingEntity entity = entities.get(target.entityId());
            PostureRuntime posture = postureAt(target.entityId(), currentTick);
            boolean postureBroken = postures.phaseAt(posture, currentTick) == PosturePhase.BROKEN;
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
                                    postureBroken
                                            ? java.util.Set.of(ConditionalAdvantage.POSTURE_BREAK)
                                            : java.util.Set.of(),
                                    trainingMove.profiles().pveMultiplier()));
            totalDamage += breakdown.finalDamage();
            trainingTargetHealth.compute(
                    target.entityId(),
                    (ignored, current) ->
                            Math.max(
                                    0,
                                    (current == null ? 1000.0 : current)
                                            - breakdown.finalDamage()));
            PostureResolution postureResolution =
                    postures.damage(posture, currentTick, trainingMove.outputs().posture());
            trainingTargetPosture.put(target.entityId(), postureResolution.runtime());
            if (postureResolution.justBroke()) {
                postureBreaks++;
            }
            if (firstPosture == null) {
                firstPosture = postureLabel(postureResolution.runtime(), currentTick);
            }
        }
        session.lastResolution =
                "HIT targets="
                        + resolved.size()
                        + " damage="
                        + Math.round(totalDamage * 10.0) / 10.0
                        + " posture="
                        + firstPosture
                        + (postureBreaks > 0 ? " breaks=" + postureBreaks : "");
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.GREEN));
    }

    private void renderArcDebug(
            Player owner,
            ArcHitboxQuery previousQuery,
            ArcHitboxQuery currentQuery,
            List<CombatVector> sampledOrigins,
            List<TargetCollider> candidates,
            List<ResolvedTarget> resolved,
            Map<UUID, LivingEntity> entities) {
        java.util.Set<UUID> viewerIds = debugViewers.get(owner.getUniqueId());
        if (viewerIds == null || viewerIds.isEmpty()) {
            return;
        }
        java.util.Set<UUID> selected =
                resolved.stream()
                        .map(ResolvedTarget::entityId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<CombatVector> previousOutline = arcDebugGeometry.outline(previousQuery, 12, 4);
        List<CombatVector> currentOutline = arcDebugGeometry.outline(currentQuery, 12, 4);
        for (UUID viewerId : List.copyOf(viewerIds)) {
            Player viewer = plugin.getServer().getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline() || viewer.getWorld() != owner.getWorld()) {
                continue;
            }
            for (CombatVector point : previousOutline) {
                viewer.spawnParticle(
                        Particle.ELECTRIC_SPARK,
                        new Location(owner.getWorld(), point.x(), point.y() + 0.15, point.z()),
                        1,
                        0,
                        0,
                        0,
                        0);
            }
            for (CombatVector point : currentOutline) {
                viewer.spawnParticle(
                        Particle.END_ROD,
                        new Location(owner.getWorld(), point.x(), point.y() + 0.15, point.z()),
                        1,
                        0,
                        0,
                        0,
                        0);
            }
            for (CombatVector point : sampledOrigins) {
                viewer.spawnParticle(
                        Particle.CLOUD,
                        new Location(owner.getWorld(), point.x(), point.y() + 0.1, point.z()),
                        1,
                        0,
                        0,
                        0,
                        0);
            }
            for (TargetCollider candidate : candidates) {
                LivingEntity entity = entities.get(candidate.entityId());
                if (entity == null) {
                    continue;
                }
                Particle marker =
                        selected.contains(candidate.entityId())
                                ? Particle.HAPPY_VILLAGER
                                : candidate.lineOfSight()
                                        ? Particle.SMOKE
                                        : Particle.ANGRY_VILLAGER;
                viewer.spawnParticle(
                        marker,
                        entity.getLocation().add(0, entity.getHeight() * 0.6, 0),
                        4,
                        0.15,
                        0.15,
                        0.15,
                        0);
            }
        }
    }

    private static ArcHitboxQuery arcQuery(
            MoveDefinition.Hitbox hitbox, CombatTransform transform) {
        return new ArcHitboxQuery(
                transform.origin(),
                transform.forward(),
                hitbox.range(),
                hitbox.angleDegrees(),
                -0.5,
                hitbox.height(),
                hitbox.maxTargets());
    }

    private static CombatTransform combatTransform(Player player) {
        Location location = player.getLocation();
        org.bukkit.util.Vector direction = location.getDirection().setY(0);
        if (direction.lengthSquared() < 1.0e-9) {
            double yaw = Math.toRadians(location.getYaw());
            direction = new org.bukkit.util.Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        return new CombatTransform(
                player.getWorld().getUID(),
                new CombatVector(location.getX(), location.getY(), location.getZ()),
                new CombatVector(direction.getX(), 0, direction.getZ()).normalizedHorizontal());
    }

    private static CombatVector midpoint(CombatVector first, CombatVector second) {
        return new CombatVector(
                (first.x() + second.x()) / 2.0,
                (first.y() + second.y()) / 2.0,
                (first.z() + second.z()) / 2.0);
    }

    private PostureRuntime postureAt(UUID entityId, long tick) {
        PostureRuntime current =
                trainingTargetPosture.computeIfAbsent(
                        entityId, ignored -> PostureRuntime.initial(postures.profile(), tick));
        PostureRuntime advanced = postures.tick(current, tick);
        trainingTargetPosture.put(entityId, advanced);
        return advanced;
    }

    private PostureResolution applyPostureDamage(UUID entityId, long tick, double amount) {
        PostureResolution resolution = postures.damage(postureAt(entityId, tick), tick, amount);
        trainingTargetPosture.put(entityId, resolution.runtime());
        return resolution;
    }

    private String postureLabel(PostureRuntime runtime, long tick) {
        if (postures.phaseAt(runtime, tick) == PosturePhase.BROKEN) {
            return "BROKEN(" + Math.max(0, runtime.brokenUntilTick() - tick) + "t)";
        }
        return roundOne(runtime.current()) + "/" + roundOne(postures.profile().maximum());
    }

    private void tickTrainingPosture() {
        long tick = plugin.getServer().getCurrentTick();
        java.util.Iterator<Map.Entry<UUID, PostureRuntime>> iterator =
                trainingTargetPosture.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PostureRuntime> entry = iterator.next();
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                iterator.remove();
                trainingTargetHealth.remove(entry.getKey());
                continue;
            }
            entry.setValue(postures.tick(entry.getValue(), tick));
        }
    }

    private void tickCrowdControl(LiveSession session) {
        boolean wasActive = session.crowdControl.active().isPresent();
        session.crowdControl =
                crowdControl.tick(session.crowdControl, plugin.getServer().getCurrentTick());
        if (wasActive && session.crowdControl.active().isEmpty()) {
            session.action = ActionState.IDLE;
        }
    }

    private void applyCc(
            Player player,
            LiveSession session,
            CcSeverity severity,
            int durationTicks,
            boolean pvp) {
        long tick = plugin.getServer().getCurrentTick();
        CcApplication application =
                crowdControl.apply(
                        session.crowdControl,
                        tick,
                        new CcRequest(severity, durationTicks, false, pvp));
        session.crowdControl = application.runtime();
        if (!application.applied()) {
            session.lastResolution = "CC " + severity + " " + application.outcome();
            return;
        }
        cancelAction(session, "CC_" + severity);
        session.input.clearBuffer(InputBufferClearReason.HARD_CC);
        releaseGuard(session);
        session.dodge = null;
        session.dodgeDirection = null;
        session.lastDodgeMovementElapsed = -1;
        session.sneakPress = null;
        ActionState forcedAction = actionState(severity);
        session.action = forcedAction;
        session.weapon = weapons.interrupt(session.weapon, forcedAction);
        session.lastResolution =
                "CC "
                        + severity
                        + " "
                        + application.outcome()
                        + " duration="
                        + application.effectiveDurationTicks()
                        + "t";
        player.sendActionBar(Component.text(session.lastResolution, NamedTextColor.RED));
    }

    private static ActionState actionState(CcSeverity severity) {
        return switch (severity) {
            case FLINCH, STAGGER, HEAVY_STAGGER, KNOCKBACK -> ActionState.STAGGERED;
            case KNOCKDOWN, LAUNCH -> ActionState.KNOCKED_DOWN;
            case GRAB -> ActionState.GRABBED;
        };
    }

    private void regenerateStamina(LiveSession session) {
        if (session.timeline != null
                || session.dodge != null
                || session.guard.active()
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

    private void tickSneakPress(Player player, LiveSession session) {
        if (session.sneakPress == null) {
            return;
        }
        DirectionSnapshot direction = direction(player.getCurrentInput());
        SneakPressDecision decision =
                sneakPresses.resolve(
                        session.sneakPress,
                        plugin.getServer().getCurrentTick(),
                        player.isSneaking(),
                        direction);
        if (decision == SneakPressDecision.DODGE) {
            session.sneakPress = null;
            player.setSneaking(false);
            requestDodge(player, session, direction);
        } else if (decision != SneakPressDecision.WAITING) {
            session.sneakPress = null;
        }
    }

    private void requestDodge(Player player, LiveSession session, DirectionSnapshot direction) {
        long tick = plugin.getServer().getCurrentTick();
        if (!dodgeWindowOpen(session)) {
            player.sendActionBar(Component.text("Dodge window is closed.", NamedTextColor.RED));
            return;
        }
        Result<SemanticInput, InputRejectionCode> semantic =
                inputPolicy.resolve(
                        ClientAction.SNEAK_PRESS, policyContext(player, session, direction));
        if (!(semantic instanceof Result.Success<SemanticInput, InputRejectionCode> intent)
                || intent.value() != SemanticInput.DODGE) {
            return;
        }
        CombatResources postCancelResources = resourcesAfterDodgeCancel(session);
        Result<DodgeRuntime, DodgeErrorCode> started =
                dodges.start(
                        Optional.ofNullable(session.dodge),
                        dodgeProfile,
                        direction,
                        postCancelResources.availableStamina(),
                        tick);
        if (started instanceof Result.Failure<DodgeRuntime, DodgeErrorCode> failure) {
            player.sendActionBar(
                    Component.text("Dodge rejected: " + failure.error(), NamedTextColor.RED));
            return;
        }
        Result<CombatInputRequest, InputRejectionCode> observed =
                session.input.observe(
                        new InputObservation(
                                tick,
                                intent.value(),
                                direction,
                                "dodge",
                                new InputDeduplicationKey("FEET", "SNEAK_PRESS")));
        if (!(observed instanceof Result.Success<CombatInputRequest, InputRejectionCode> success)) {
            return;
        }
        Result<InputRouteOutcome, InputRejectionCode> routed =
                session.input.routeFrame(
                        List.of(success.value()), dodgeRoutingContext(player, session));
        if (!(routed instanceof Result.Success<InputRouteOutcome, InputRejectionCode>)) {
            return;
        }

        DodgeRuntime runtime = ((Result.Success<DodgeRuntime, DodgeErrorCode>) started).value();
        session.resources =
                postCancelResources.spendStamina(dodgeProfile.staminaCost()).orElseThrow();
        releaseGuard(session);
        session.timeline = null;
        session.previousActionTransform = null;
        session.action = ActionState.IDLE;
        session.dodge = runtime;
        session.dodgeDirection = dodgeVector(player, direction);
        session.lastDodgeMovementElapsed = -1;
        session.lastStaminaSpendTick = tick;
        session.staminaRegenRemainder = 0;
        applyDodgeMovement(player, session, tick);
        player.sendActionBar(
                Component.text(
                        "DODGE " + dodgeProfile.load() + " stamina=" + session.resources.stamina(),
                        NamedTextColor.AQUA));
    }

    private void tickDodge(Player player, LiveSession session) {
        if (session.dodge == null) {
            return;
        }
        long tick = plugin.getServer().getCurrentTick();
        applyDodgeMovement(player, session, tick);
        DodgePhase phase = session.dodge.phaseAt(tick);
        if (phase == DodgePhase.COMPLETE) {
            session.dodge = null;
            session.dodgeDirection = null;
            session.lastDodgeMovementElapsed = -1;
        }
    }

    private void applyDodgeMovement(Player player, LiveSession session, long tick) {
        if (session.dodge == null || !session.dodge.movementAppliesAt(tick)) {
            return;
        }
        long elapsed = session.dodge.elapsed(tick);
        if (elapsed <= session.lastDodgeMovementElapsed) {
            return;
        }
        session.lastDodgeMovementElapsed = elapsed;
        double step = dodgeProfile.travelDistance() / dodgeProfile.movementTicks();
        org.bukkit.util.Vector displacement = session.dodgeDirection.clone().multiply(step);
        if (!collisionFree(player, displacement)) {
            session.lastResolution = "DODGE blocked by world collision";
            return;
        }
        org.bukkit.Location destination = player.getLocation().add(displacement);
        session.applyingDodgeTeleport = true;
        try {
            player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } finally {
            session.applyingDodgeTeleport = false;
        }
    }

    private static boolean collisionFree(Player player, org.bukkit.util.Vector displacement) {
        org.bukkit.util.BoundingBox destination =
                player.getBoundingBox().clone().shift(displacement);
        int minX = (int) Math.floor(destination.getMinX());
        int maxX = (int) Math.floor(destination.getMaxX());
        int minY = (int) Math.floor(destination.getMinY());
        int maxY = (int) Math.floor(destination.getMaxY());
        int minZ = (int) Math.floor(destination.getMinZ());
        int maxZ = (int) Math.floor(destination.getMaxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (player.getWorld()
                            .getBlockAt(x, y, z)
                            .getCollisionShape()
                            .overlaps(destination)) {
                        return false;
                    }
                }
            }
        }
        return player.getWorld()
                        .rayTraceBlocks(
                                player.getLocation().add(0, 0.1, 0),
                                displacement,
                                displacement.length(),
                                FluidCollisionMode.NEVER,
                                true)
                == null;
    }

    private CombatResources resourcesAfterDodgeCancel(LiveSession session) {
        if (session.timeline == null) {
            return session.resources;
        }
        int cancellationTick = session.timeline.tick();
        Result<ActionTimeline, ActionTimelineErrorCode> cancelled =
                session.timeline.cancel("DODGE");
        if (cancelled instanceof Result.Success<ActionTimeline, ActionTimelineErrorCode> success) {
            session.activeTraceCommands.add(
                    new ActionSimulationCommand(
                            cancellationTick, ActionSimulationCommand.Type.CANCEL, "DODGE"));
            finishTrace(session, success.value());
            return success.value().resources();
        }
        return session.timeline.resources();
    }

    private void toggleGuard(Player player, LiveSession session) {
        long tick = plugin.getServer().getCurrentTick();
        Result<CombatInputRequest, InputRejectionCode> observed =
                session.input.observe(
                        new InputObservation(
                                tick,
                                SemanticInput.DEFENSIVE_RESPONSE,
                                DirectionSnapshot.NEUTRAL,
                                "weapon_guard",
                                new InputDeduplicationKey("MAIN_HAND", "GUARD_TOGGLE")));
        if (!(observed instanceof Result.Success<CombatInputRequest, InputRejectionCode> input)) {
            return;
        }
        Result<InputRouteOutcome, InputRejectionCode> routed =
                session.input.routeFrame(List.of(input.value()), routingContext(player, session));
        if (!(routed instanceof Result.Success<InputRouteOutcome, InputRejectionCode>)) {
            player.sendActionBar(Component.text("Guard is action-locked.", NamedTextColor.RED));
            return;
        }
        if (session.guard.active()) {
            releaseGuard(session);
            player.sendActionBar(Component.text("GUARD released", NamedTextColor.GRAY));
            return;
        }
        Result<GuardRuntime, GuardErrorCode> started = guards.start(session.guard, tick);
        if (started instanceof Result.Failure<GuardRuntime, GuardErrorCode> failure) {
            player.sendActionBar(
                    Component.text("Guard rejected: " + failure.error(), NamedTextColor.RED));
            return;
        }
        session.guard = ((Result.Success<GuardRuntime, GuardErrorCode>) started).value();
        player.sendActionBar(Component.text("WEAPON GUARD", NamedTextColor.YELLOW));
    }

    private void releaseGuard(LiveSession session) {
        if (!session.guard.active()) {
            return;
        }
        Result<GuardRuntime, GuardErrorCode> released =
                guards.release(session.guard, plugin.getServer().getCurrentTick());
        if (released instanceof Result.Success<GuardRuntime, GuardErrorCode> success) {
            session.guard = success.value();
        }
    }

    private GuardHitRequest guardHitRequest(
            Player defender, LiveSession session, LivingEntity source, double incomingDamage) {
        org.bukkit.Location facingLocation = defender.getLocation().clone();
        facingLocation.setPitch(0);
        org.bukkit.util.Vector facing = facingLocation.getDirection();
        org.bukkit.util.Vector toAttacker =
                source.getLocation().toVector().subtract(defender.getLocation().toVector());
        toAttacker.setY(0);
        if (toAttacker.lengthSquared() < 1.0e-9) {
            toAttacker = facing.clone();
        }
        return new GuardHitRequest(
                incomingDamage,
                trainingIncomingGuardPressure,
                true,
                true,
                new CombatVector(facing.getX(), 0, facing.getZ()),
                new CombatVector(toAttacker.getX(), 0, toAttacker.getZ()),
                session.resources.availableStamina());
    }

    private static double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private InputRoutingContext dodgeRoutingContext(Player player, LiveSession session) {
        InputRoutingContext base = routingContext(player, session);
        if (base.legalNow().contains(SemanticInput.DODGE)) {
            return base;
        }
        java.util.EnumSet<SemanticInput> legal = java.util.EnumSet.copyOf(base.legalNow());
        legal.add(SemanticInput.DODGE);
        return new InputRoutingContext(legal, base.bufferWindowOpen());
    }

    private boolean combatDodgeContext(LiveSession session) {
        return session.engagement.state() != EngagementState.EXPLORATION
                && (session.weapon.state() == WeaponState.READY
                        || session.weapon.state() == WeaponState.DRAWING);
    }

    private boolean dodgeWindowOpen(LiveSession session) {
        if (!combatDodgeContext(session) || session.action.hardControl()) {
            return false;
        }
        return session.timeline == null
                || session.timeline.tick() >= trainingMove.cancels().dodgeFromTick();
    }

    private static DirectionSnapshot direction(Input input) {
        double forward = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
        double strafe = (input.isLeft() ? 1 : 0) - (input.isRight() ? 1 : 0);
        return DirectionSnapshot.fromAxes(forward, strafe);
    }

    private static org.bukkit.util.Vector dodgeVector(Player player, DirectionSnapshot direction) {
        org.bukkit.util.Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 1.0e-9) {
            forward = new org.bukkit.util.Vector(0, 0, 1);
        } else {
            forward.normalize();
        }
        org.bukkit.util.Vector left =
                new org.bukkit.util.Vector(forward.getZ(), 0, -forward.getX());
        return switch (direction) {
            case FORWARD -> forward;
            case BACK -> forward.multiply(-1);
            case LEFT -> left;
            case RIGHT -> left.multiply(-1);
            case NEUTRAL -> throw new IllegalArgumentException("neutral dodge direction");
        };
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
        if (session.engagement.state() != EngagementState.ENGAGED) {
            releaseGuard(session);
        }
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
        if (session.timeline != null || session.dodge != null || session.guard.active()) {
            if (session.dodge != null) {
                player.sendActionBar(
                        Component.text("Attack locked during dodge recovery.", NamedTextColor.RED));
            }
            if (session.guard.active()) {
                player.sendActionBar(
                        Component.text("Release guard before attacking.", NamedTextColor.RED));
            }
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
        session.previousActionTransform = combatTransform(player);
        session.activeTraceInitialResources = session.resources;
        session.activeTraceCommands.clear();
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
        int cancellationTick = session.timeline.tick();
        Result<ActionTimeline, ActionTimelineErrorCode> cancelled = session.timeline.cancel(reason);
        if (cancelled instanceof Result.Success<ActionTimeline, ActionTimelineErrorCode> success) {
            session.resources = success.value().resources();
            session.activeTraceCommands.add(
                    new ActionSimulationCommand(
                            cancellationTick, ActionSimulationCommand.Type.CANCEL, reason));
            finishTrace(session, success.value());
        }
        session.timeline = null;
        session.previousActionTransform = null;
        session.action = ActionState.IDLE;
    }

    private void finishTrace(LiveSession session, ActionTimeline terminalTimeline) {
        if (session.activeTraceInitialResources == null) {
            return;
        }
        session.lastTrace =
                new CombatTrace(
                        contentVersion,
                        trainingMove.id(),
                        session.activeTraceInitialResources,
                        session.activeTraceCommands,
                        terminalTimeline.trace(),
                        terminalTimeline.resources(),
                        terminalTimeline.phase());
        session.activeTraceInitialResources = null;
        session.activeTraceCommands.clear();
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
        if (session.dodge != null) {
            return InputRoutingContext.legal(
                    java.util.EnumSet.of(
                            SemanticInput.FORCED_INTERRUPT, SemanticInput.UI_DANGER_CLOSE));
        }
        return inputPolicy.routingContext(policyContext(player, session), false);
    }

    private InputPolicyContext policyContext(Player player, LiveSession session) {
        return policyContext(player, session, DirectionSnapshot.NEUTRAL);
    }

    private InputPolicyContext policyContext(
            Player player, LiveSession session, DirectionSnapshot direction) {
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
                direction);
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

    private record CombatTransform(UUID worldId, CombatVector origin, CombatVector forward) {
        private CombatTransform {
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(origin, "origin");
            forward = Objects.requireNonNull(forward, "forward").normalizedHorizontal();
        }
    }

    private static final class LiveSession {
        private EngagementRuntime engagement;
        private WeaponTransitionSnapshot weapon = WeaponTransitionSnapshot.initial();
        private ActionState action = ActionState.IDLE;
        private CombatResources resources = CombatResources.full(1000, 100, 100);
        private final InputRouter input = new InputRouter();
        private ActionTimeline timeline;
        private CombatTransform previousActionTransform;
        private DodgeRuntime dodge;
        private GuardRuntime guard;
        private PoiseRuntime poise;
        private CcRuntime crowdControl;
        private CombatResources activeTraceInitialResources;
        private final java.util.List<ActionSimulationCommand> activeTraceCommands =
                new java.util.ArrayList<>();
        private CombatTrace lastTrace;
        private org.bukkit.util.Vector dodgeDirection;
        private boolean applyingDodgeTeleport;
        private long lastDodgeMovementElapsed = -1;
        private SneakPressWindow sneakPress;
        private long lastStaminaSpendTick = Long.MIN_VALUE / 2;
        private double staminaRegenRemainder;
        private String lastResolution;
        private long lastDefenseTick = Long.MIN_VALUE / 2;
        private CombatDefenseOutcome lastDefenseOutcome = CombatDefenseOutcome.HIT;
        private final java.util.Set<UUID> threatOwners = new java.util.HashSet<>();

        private LiveSession(
                EngagementRuntime engagement,
                GuardRuntime guard,
                PoiseRuntime poise,
                CcRuntime crowdControl) {
            this.engagement = Objects.requireNonNull(engagement, "engagement");
            this.guard = Objects.requireNonNull(guard, "guard");
            this.poise = Objects.requireNonNull(poise, "poise");
            this.crowdControl = Objects.requireNonNull(crowdControl, "crowdControl");
        }
    }
}
