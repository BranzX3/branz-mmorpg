package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.combat.input.DirectionSnapshot;
import com.branz.mmorpg.combat.input.InputRouteDecision;
import com.branz.mmorpg.combat.input.InputRouteOutcome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Real-client acceptance for directional PRIMARY resolution and the authored one-slot buffer. */
final class DirectionalBufferClientAcceptanceProbe implements Listener {
    static final String ENABLE_PROPERTY = "mmo.bootstrap.directional-buffer-acceptance-test";
    static final String MARKER_PROPERTY = "mmo.bootstrap.directional-buffer-acceptance-marker";
    static final String PASS_MARKER = "DIRECTIONAL_BUFFER_CLIENT_ACCEPTANCE_PASS";

    private static final DefinitionId FORWARD_MOVE =
            DefinitionId.of("move.training_greatsword.forward_drive");
    private static final DefinitionId FOLLOWUP_MOVE =
            DefinitionId.of("move.training_greatsword.followup_crosscut");
    private static final String FORWARD_BRANCH = "PRIMARY_DIRECTIONAL_FORWARD";
    private static final String FOLLOWUP_BRANCH = "PRIMARY_2";
    private static final String TRAINING_DUMMY_TAG = "branzmmo.directional_buffer_dummy";
    private static final int QUEUE_WINDOW_ARMED_LEVEL = 6;
    private static final long QUEUE_WINDOW_ARM_DELAY_TICKS = 12L;

    private final JavaPlugin plugin;
    private final CombatSessionController combat;
    private UUID playerId;
    private UUID trainingDummyId;
    private int readyCount;
    private int routeStage;
    private boolean forwardMoveHit;
    private boolean completed;

    private DirectionalBufferClientAcceptanceProbe(
            JavaPlugin plugin, CombatSessionController combat) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.combat = Objects.requireNonNull(combat, "combat");
    }

    static SuccessfulCombatActionObserver install(
            JavaPlugin plugin,
            StartingFoundationController foundations,
            CombatSessionController combat) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return SuccessfulCombatActionObserver.NONE;
        }
        if (Boolean.getBoolean(OnboardingClientAcceptanceProbe.ENABLE_PROPERTY)) {
            throw new IllegalStateException(
                    "directional-buffer and onboarding-defense acceptance modes are mutually exclusive");
        }
        DirectionalBufferClientAcceptanceProbe probe =
                new DirectionalBufferClientAcceptanceProbe(plugin, combat);
        foundations.setFoundationReadyObserver(probe::onFoundationReady);
        combat.setPrimaryRouteObserver(probe::onPrimaryRoute);
        plugin.getServer().getPluginManager().registerEvents(probe, plugin);
        plugin.getServer().getScheduler().runTaskLater(plugin, probe::timeout, 20L * 120L);
        plugin.getLogger().info("DIRECTIONAL_BUFFER_CLIENT_ACCEPTANCE_ARMED");
        return probe::onSuccessfulCombatAction;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmSwingObserved(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING
                || playerId == null
                || !playerId.equals(event.getPlayer().getUniqueId())) {
            return;
        }
        plugin.getLogger()
                .info(
                        "DIRECTIONAL_BUFFER_ARM_SWING_OBSERVED stage="
                                + routeStage
                                + " tick="
                                + plugin.getServer().getCurrentTick()
                                + " cancelled="
                                + event.isCancelled());
    }

    private void onFoundationReady(Player player, StartingFoundation foundation) {
        if (completed) {
            return;
        }
        if (foundation != StartingFoundation.GREATSWORD) {
            fail("expected GREATSWORD, got " + foundation.name());
            return;
        }
        if (playerId == null) {
            playerId = player.getUniqueId();
        } else if (!playerId.equals(player.getUniqueId())) {
            fail("reconnect used a different player identity");
            return;
        }
        readyCount++;
        if (readyCount == 1) {
            plugin.getLogger().info("DIRECTIONAL_BUFFER_CLIENT_ACCEPTANCE_FIRST_READY");
            plugin.getServer()
                    .getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                if (player.isOnline()) {
                                    player.kick(
                                            Component.text(
                                                    "DIRECTIONAL_BUFFER_ACCEPTANCE_RECONNECT"));
                                }
                            },
                            2L);
            return;
        }
        if (readyCount != 2) {
            fail("foundation ready observer fired too many times");
            return;
        }
        spawnTrainingDummy(player);
        plugin.getLogger().info("DIRECTIONAL_BUFFER_CLIENT_ACCEPTANCE_RECONNECT_READY");
    }

    private void onPrimaryRoute(Player player, InputRouteOutcome outcome) {
        if (completed
                || readyCount != 2
                || playerId == null
                || !playerId.equals(player.getUniqueId())) {
            return;
        }
        switch (routeStage) {
            case 0 -> verifyForwardExecution(player, outcome);
            case 1 -> verifyInitialBuffer(player, outcome);
            default -> fail("unexpected third primary route before buffered follow-up resolved");
        }
    }

    private void verifyForwardExecution(Player player, InputRouteOutcome outcome) {
        if (outcome.decision() != InputRouteDecision.EXECUTED
                || outcome.request().direction() != DirectionSnapshot.FORWARD
                || !outcome.request().branchFamily().equals(FORWARD_BRANCH)) {
            fail("first route was not EXECUTED FORWARD " + FORWARD_BRANCH + ": " + outcome);
            return;
        }
        routeStage = 1;
        plugin.getLogger()
                .info(
                        "DIRECTIONAL_BUFFER_FORWARD_ROUTE_PASS sequence="
                                + outcome.request().sequence()
                                + " observedTick="
                                + outcome.request().observedTick());
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (completed
                                    || routeStage != 1
                                    || !player.isOnline()
                                    || playerId == null
                                    || !playerId.equals(player.getUniqueId())) {
                                return;
                            }
                            player.setLevel(QUEUE_WINDOW_ARMED_LEVEL);
                            plugin.getLogger()
                                    .info(
                                            "DIRECTIONAL_BUFFER_QUEUE_WINDOW_ARMED serverDelay="
                                                    + QUEUE_WINDOW_ARM_DELAY_TICKS);
                        },
                        QUEUE_WINDOW_ARM_DELAY_TICKS);
    }

    private void verifyInitialBuffer(Player player, InputRouteOutcome outcome) {
        if (outcome.decision() != InputRouteDecision.BUFFERED
                || !outcome.request().branchFamily().equals(FOLLOWUP_BRANCH)) {
            fail("second route was not BUFFERED " + FOLLOWUP_BRANCH + ": " + outcome);
            return;
        }
        routeStage = 2;
        player.setLevel(0);
        plugin.getLogger()
                .info(
                        "DIRECTIONAL_BUFFER_ONE_SLOT_BUFFERED_PASS sequence="
                                + outcome.request().sequence()
                                + " observedTick="
                                + outcome.request().observedTick());
    }

    private void onSuccessfulCombatAction(
            CharacterId actorId, UUID actionId, DefinitionId moveId, long currentTick) {
        if (completed || playerId == null || !actorId.value().equals(playerId) || readyCount != 2) {
            return;
        }
        if (moveId.equals(FORWARD_MOVE)) {
            if (routeStage < 1) {
                fail("forward move committed before its directional route was observed");
                return;
            }
            forwardMoveHit = true;
            plugin.getLogger()
                    .info(
                            "DIRECTIONAL_BUFFER_FORWARD_MOVE_PASS action="
                                    + actionId
                                    + " tick="
                                    + currentTick);
            return;
        }
        if (!moveId.equals(FOLLOWUP_MOVE)) {
            return;
        }
        if (!forwardMoveHit || routeStage != 2) {
            fail("follow-up move resolved before the one-slot buffer contract completed");
            return;
        }
        try {
            writeMarker();
        } catch (IOException exception) {
            fail("marker write failed: " + exception.getMessage());
            return;
        }
        completed = true;
        removeTrainingDummy();
        plugin.getLogger()
                .info(
                        PASS_MARKER
                                + " move="
                                + moveId.value()
                                + " routeStages="
                                + routeStage
                                + " tick="
                                + currentTick);
        plugin.getServer().getScheduler().runTaskLater(plugin, plugin.getServer()::shutdown, 2L);
    }

    private void spawnTrainingDummy(Player player) {
        removeTrainingDummy();
        Location origin = player.getLocation().clone();
        Vector forward = origin.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0e-6) {
            forward = new Vector(0, 0, 1);
        } else {
            forward.normalize();
        }
        Pig dummy = player.getWorld().spawn(origin.add(forward.multiply(1.75)), Pig.class);
        dummy.setAI(false);
        dummy.setSilent(true);
        dummy.setRemoveWhenFarAway(false);
        dummy.addScoreboardTag(TRAINING_DUMMY_TAG);
        if (dummy.getAttribute(Attribute.MAX_HEALTH) != null) {
            Objects.requireNonNull(dummy.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(200.0);
            dummy.setHealth(200.0);
        }
        trainingDummyId = dummy.getUniqueId();
        plugin.getLogger()
                .info(
                        "DIRECTIONAL_BUFFER_CLIENT_ACCEPTANCE_DUMMY_READY id="
                                + trainingDummyId
                                + " distance="
                                + Math.sqrt(
                                        dummy.getLocation().distanceSquared(player.getLocation())));
    }

    private void removeTrainingDummy() {
        if (trainingDummyId == null) {
            return;
        }
        Entity dummy = plugin.getServer().getEntity(trainingDummyId);
        if (dummy != null) {
            dummy.remove();
        }
        trainingDummyId = null;
    }

    private void timeout() {
        if (!completed) {
            fail("timed out before directional branch and one-slot follow-up completed");
        }
    }

    private void fail(String detail) {
        if (completed) {
            return;
        }
        completed = true;
        removeTrainingDummy();
        plugin.getLogger().severe("DIRECTIONAL_BUFFER_CLIENT_ACCEPTANCE_FAIL " + detail);
        plugin.getServer().getScheduler().runTask(plugin, plugin.getServer()::shutdown);
    }

    private static void writeMarker() throws IOException {
        String raw = System.getProperty(MARKER_PROPERTY, "").trim();
        if (raw.isEmpty()) {
            throw new IllegalStateException("directional buffer acceptance marker path is missing");
        }
        Path marker = Path.of(raw).toAbsolutePath().normalize();
        Path parent = marker.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(marker, PASS_MARKER + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
