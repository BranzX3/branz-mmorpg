package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.action.ActionPhase;
import com.branz.mmorpg.combat.action.ActionTimeline;
import com.branz.mmorpg.combat.action.ActionTimelineErrorCode;
import com.branz.mmorpg.combat.action.CombatResources;
import com.branz.mmorpg.combat.action.ResourceCommitState;
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
import com.branz.mmorpg.combat.state.EngagementState;
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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
    private final WeaponTransitionMachine weapons;
    private final CombatInputPolicy inputPolicy = new CombatInputPolicy();
    private final Map<UUID, LiveSession> sessions = new HashMap<>();
    private int tickTaskId = -1;

    CombatSessionController(
            JavaPlugin plugin,
            CharacterSessionController characters,
            MoveEngine moves,
            int drawTicks,
            int sheatheTicks) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        Objects.requireNonNull(moves, "moves");
        trainingMove =
                moves.find(TRAINING_MOVE)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "active content is missing " + TRAINING_MOVE));
        weapons = new WeaponTransitionMachine(drawTicks, sheatheTicks);
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
        LiveSession session = new LiveSession();
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
                        session.weapon.state(),
                        Optional.ofNullable(session.timeline).map(ActionTimeline::phase),
                        resources.stamina(),
                        resources.reservedStamina()));
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
            regenerateStamina(session);
        }
    }

    private void tickAction(Player player, LiveSession session) {
        if (session.timeline == null) {
            return;
        }
        ActionPhase prior = session.timeline.phase();
        ResourceCommitState priorResourceState = session.timeline.resourceState();
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
        if (session.timeline.phase().terminal()) {
            session.resources = session.timeline.resources();
            session.timeline = null;
            session.action = ActionState.IDLE;
        } else {
            session.action = actionState(session.timeline.phase());
        }
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
                EngagementState.EXPLORATION,
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
        private WeaponTransitionSnapshot weapon = WeaponTransitionSnapshot.initial();
        private ActionState action = ActionState.IDLE;
        private CombatResources resources = CombatResources.full(1000, 100, 100);
        private final InputRouter input = new InputRouter();
        private ActionTimeline timeline;
        private long lastStaminaSpendTick = Long.MIN_VALUE / 2;
        private double staminaRegenRemainder;
    }
}
