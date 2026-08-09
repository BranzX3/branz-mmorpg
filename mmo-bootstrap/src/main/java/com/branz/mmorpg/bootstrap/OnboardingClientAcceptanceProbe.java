package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.dodge.DodgePhase;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.persistence.transaction.CharacterOnboardingStateRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Real-client acceptance for first choice -> durable kit -> reconnect -> combat -> dodge. */
final class OnboardingClientAcceptanceProbe {
    static final String ENABLE_PROPERTY = "mmo.bootstrap.onboarding-acceptance-test";
    static final String MARKER_PROPERTY = "mmo.bootstrap.onboarding-acceptance-marker";
    static final String MODE_PROPERTY = "mmo.bootstrap.onboarding-acceptance-mode";
    static final String PASS_MARKER = "ONBOARDING_CLIENT_ACCEPTANCE_PASS";
    private static final String FIRST_COMBAT_MARKER = "ONBOARDING_FIRST_COMBAT_GATE_PASS";
    private static final String DEFENSE_DODGE_MARKER = "ONBOARDING_DEFENSE_DODGE_GATE_PASS";
    private static final String FIRST_HOSTILE_KILL_MARKER =
            "ONBOARDING_FIRST_HOSTILE_KILL_GATE_PASS";
    private static final String TRAINING_DUMMY_TAG = "branzmmo.training_dummy";
    private static final String HOSTILE_ACCEPTANCE_TAG = "branzmmo.hostile_kill_acceptance";
    private static final int HOSTILE_TARGET_READY_LEVEL = 18;
    private static final int STARTER_HOTBAR_SLOT = 0;
    private static final int CHRONICLE_HOTBAR_SLOT = 8;
    private static final DefinitionId GREATSWORD = DefinitionId.of("weapon.training_greatsword");
    private static final DefinitionId GREATSWORD_MOVE =
            DefinitionId.of("move.training_greatsword.committed_cleave");

    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private final boolean hostileKillMode;
    private UUID playerId;
    private UUID trainingDummyId;
    private UUID hostileTargetId;
    private int readyCount;
    private int hostileSuccessfulActions;
    private boolean checking;
    private boolean chronicleSelected;
    private boolean combatStagingReady;
    private boolean awaitingDodge;
    private boolean awaitingHostileKill;
    private boolean completed;

    private OnboardingClientAcceptanceProbe(
            JavaPlugin plugin, CharacterSessionController characters) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.hostileKillMode =
                "hostile-kill"
                        .equalsIgnoreCase(System.getProperty(MODE_PROPERTY, "defense").trim());
    }

    static SuccessfulCombatActionObserver install(
            JavaPlugin plugin,
            StartingFoundationController foundations,
            CharacterSessionController characters) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return SuccessfulCombatActionObserver.NONE;
        }
        OnboardingClientAcceptanceProbe probe =
                new OnboardingClientAcceptanceProbe(plugin, characters);
        foundations.setFoundationReadyObserver(probe::onFoundationReady);
        plugin.getServer().getScheduler().runTaskLater(plugin, probe::timeout, 20L * 120L);
        plugin.getLogger()
                .info(
                        "ONBOARDING_CLIENT_ACCEPTANCE_ARMED mode="
                                + (probe.hostileKillMode ? "hostile-kill" : "defense"));
        return probe::onSuccessfulCombatAction;
    }

    private void onFoundationReady(Player player, StartingFoundation foundation) {
        if (completed || checking) {
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
        checking = true;
        characters.startingFoundationState(player, result -> verifyDurableState(player, result));
    }

    private void verifyDurableState(
            Player player,
            Result<Optional<CharacterOnboardingStateRecord>, CharacterSessionErrorCode> result) {
        if (completed) {
            return;
        }
        if (result
                instanceof
                Result.Failure<Optional<CharacterOnboardingStateRecord>, CharacterSessionErrorCode>
                        failure) {
            fail("state read failed: " + failure.error().code() + " " + failure.detail());
            return;
        }
        CharacterOnboardingStateRecord record =
                ((Result.Success<
                                        Optional<CharacterOnboardingStateRecord>,
                                        CharacterSessionErrorCode>)
                                result)
                        .value()
                        .orElse(null);
        if (record == null || !record.kitReady() || !record.foundationId().equals("GREATSWORD")) {
            fail("durable onboarding state is incomplete");
            return;
        }
        try {
            validateGreatswordProjection(player);
        } catch (RuntimeException exception) {
            fail(exception.getMessage());
            return;
        }

        readyCount++;
        checking = false;
        if (readyCount == 1) {
            plugin.getLogger().info("ONBOARDING_CLIENT_ACCEPTANCE_FIRST_READY");
            plugin.getServer()
                    .getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                if (player.isOnline()) {
                                    player.kick(Component.text("ONBOARDING_ACCEPTANCE_RECONNECT"));
                                }
                            },
                            2L);
            return;
        }
        if (readyCount != 2) {
            fail("foundation ready observer fired too many times");
            return;
        }
        plugin.getLogger().info("ONBOARDING_CLIENT_ACCEPTANCE_RECONNECT_READY");
        plugin.getServer().getScheduler().runTaskLater(plugin, this::pollCombatStaging, 1L);
    }

    private void pollCombatStaging() {
        if (completed || combatStagingReady) {
            return;
        }
        Player player = playerId == null ? null : plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            fail("player disconnected before combat staging completed");
            return;
        }
        if (!chronicleSelected) {
            if (player.getInventory().getHeldItemSlot() == CHRONICLE_HOTBAR_SLOT
                    && player.getInventory().getItemInMainHand().getType()
                            == Material.WRITTEN_BOOK) {
                chronicleSelected = true;
                plugin.getLogger().info("ONBOARDING_CLIENT_ACCEPTANCE_CHRONICLE_HELD");
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, this::pollCombatStaging, 1L);
            return;
        }
        if (player.getInventory().getHeldItemSlot() != STARTER_HOTBAR_SLOT
                || player.getInventory().getItemInMainHand().getType().isAir()
                || player.getInventory().getItemInMainHand().getType() == Material.WRITTEN_BOOK) {
            plugin.getServer().getScheduler().runTaskLater(plugin, this::pollCombatStaging, 1L);
            return;
        }
        combatStagingReady = true;
        spawnTrainingDummy(player);
        plugin.getLogger().info("ONBOARDING_CLIENT_ACCEPTANCE_COMBAT_STAGING_READY");
    }

    private void spawnTrainingDummy(Player player) {
        removeTrainingDummy();
        Location target = targetLocation(player, 1.75);
        Pig dummy = player.getWorld().spawn(target, Pig.class);
        dummy.setAI(false);
        dummy.setSilent(true);
        dummy.setRemoveWhenFarAway(false);
        dummy.addScoreboardTag(TRAINING_DUMMY_TAG);
        trainingDummyId = dummy.getUniqueId();
        plugin.getLogger()
                .info(
                        "ONBOARDING_CLIENT_ACCEPTANCE_DUMMY_READY id="
                                + trainingDummyId
                                + " distance="
                                + Math.sqrt(
                                        dummy.getLocation().distanceSquared(player.getLocation())));
    }

    private void onSuccessfulCombatAction(
            CharacterId actorId, UUID actionId, DefinitionId moveId, long currentTick) {
        if (completed
                || !combatStagingReady
                || readyCount != 2
                || playerId == null
                || !actorId.value().equals(playerId)) {
            return;
        }
        if (awaitingHostileKill) {
            hostileSuccessfulActions++;
            plugin.getLogger()
                    .info(
                            "ONBOARDING_HOSTILE_COMBAT_ACTION move="
                                    + moveId.value()
                                    + " action="
                                    + actionId
                                    + " count="
                                    + hostileSuccessfulActions);
            plugin.getServer().getScheduler().runTaskLater(plugin, this::pollHostileKill, 1L);
            return;
        }
        if (awaitingDodge) {
            return;
        }
        if (!GREATSWORD_MOVE.equals(moveId)) {
            fail(
                    "expected first successful move "
                            + GREATSWORD_MOVE.value()
                            + ", got "
                            + moveId.value());
            return;
        }
        awaitingDodge = true;
        removeTrainingDummy();
        plugin.getLogger()
                .info(
                        FIRST_COMBAT_MARKER
                                + " move="
                                + moveId.value()
                                + " action="
                                + actionId
                                + " tick="
                                + currentTick);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::pollDodge, 1L);
    }

    private void pollDodge() {
        if (completed || !awaitingDodge) {
            return;
        }
        Player player = playerId == null ? null : plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            fail("player disconnected before authoritative dodge was observed");
            return;
        }
        CombatSessionController combat = combatController();
        if (combat == null) {
            fail("combat controller listener is not registered");
            return;
        }
        Optional<DodgePhase> dodgePhase =
                combat.status(player).flatMap(CombatSessionStatus::dodgePhase);
        if (dodgePhase.isEmpty() || dodgePhase.orElseThrow() == DodgePhase.COMPLETE) {
            plugin.getServer().getScheduler().runTaskLater(plugin, this::pollDodge, 1L);
            return;
        }
        DodgePhase phase = dodgePhase.orElseThrow();
        long currentTick = plugin.getServer().getCurrentTick();
        plugin.getLogger().info(DEFENSE_DODGE_MARKER + " phase=" + phase + " tick=" + currentTick);
        if (hostileKillMode) {
            awaitingDodge = false;
            awaitingHostileKill = true;
            spawnHostileTarget(player);
            player.setLevel(HOSTILE_TARGET_READY_LEVEL);
            plugin.getLogger()
                    .info(
                            "ONBOARDING_FIRST_HOSTILE_KILL_STAGING_READY level="
                                    + HOSTILE_TARGET_READY_LEVEL);
            return;
        }
        pass("move=" + GREATSWORD_MOVE.value() + " dodgePhase=" + phase + " tick=" + currentTick);
    }

    private void spawnHostileTarget(Player player) {
        removeHostileTarget();
        Creeper hostile = player.getWorld().spawn(targetLocation(player, 1.75), Creeper.class);
        hostile.setAI(false);
        hostile.setSilent(true);
        hostile.setRemoveWhenFarAway(false);
        hostile.addScoreboardTag(HOSTILE_ACCEPTANCE_TAG);
        hostileTargetId = hostile.getUniqueId();
        plugin.getLogger()
                .info(
                        "ONBOARDING_FIRST_HOSTILE_KILL_TARGET_READY id="
                                + hostileTargetId
                                + " type="
                                + hostile.getType()
                                + " distance="
                                + Math.sqrt(
                                        hostile.getLocation()
                                                .distanceSquared(player.getLocation())));
    }

    private void pollHostileKill() {
        if (completed || !awaitingHostileKill || hostileTargetId == null) {
            return;
        }
        Entity target = plugin.getServer().getEntity(hostileTargetId);
        if (target != null && !target.isDead()) {
            return;
        }
        if (hostileSuccessfulActions < 1) {
            fail("hostile target died without a successful MMO combat action");
            return;
        }
        long currentTick = plugin.getServer().getCurrentTick();
        plugin.getLogger()
                .info(
                        FIRST_HOSTILE_KILL_MARKER
                                + " actions="
                                + hostileSuccessfulActions
                                + " tick="
                                + currentTick);
        hostileTargetId = null;
        pass("hostileKill=true actions=" + hostileSuccessfulActions + " tick=" + currentTick);
    }

    private void pass(String detail) {
        try {
            writeMarker();
            completed = true;
            plugin.getLogger().info(PASS_MARKER + " " + detail);
        } catch (IOException exception) {
            fail("marker write failed: " + exception.getMessage());
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, plugin.getServer()::shutdown, 2L);
    }

    private CombatSessionController combatController() {
        for (RegisteredListener registration : HandlerList.getRegisteredListeners(plugin)) {
            if (registration.getListener() instanceof CombatSessionController combat) {
                return combat;
            }
        }
        return null;
    }

    private void validateGreatswordProjection(Player player) {
        LoadedCharacterSession session =
                characters
                        .active(player)
                        .orElseThrow(
                                () -> new IllegalStateException("character session disappeared"));
        ItemId mainHand =
                session.snapshot()
                        .equipment()
                        .item(EquipmentSlot.MAIN_HAND)
                        .orElseThrow(() -> new IllegalStateException("starter main hand is empty"));
        boolean matchingRecord =
                session.snapshot().itemRecords().stream()
                        .anyMatch(
                                item ->
                                        item.itemId().equals(mainHand)
                                                && item.definitionId().equals(GREATSWORD));
        if (!matchingRecord) {
            throw new IllegalStateException("persisted main hand is not the training greatsword");
        }
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

    private void removeHostileTarget() {
        if (hostileTargetId == null) {
            return;
        }
        Entity hostile = plugin.getServer().getEntity(hostileTargetId);
        if (hostile != null) {
            hostile.remove();
        }
        hostileTargetId = null;
    }

    private static Location targetLocation(Player player, double distance) {
        Location origin = player.getLocation().clone();
        Vector forward = origin.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0e-6) {
            forward = new Vector(0, 0, 1);
        } else {
            forward.normalize();
        }
        return origin.add(forward.multiply(distance));
    }

    private void timeout() {
        if (!completed) {
            fail(
                    hostileKillMode
                            ? "timed out before the first hostile kill completed"
                            : "timed out before onboarding completed first combat and directional dodge");
        }
    }

    private void fail(String detail) {
        if (completed) {
            return;
        }
        completed = true;
        removeTrainingDummy();
        removeHostileTarget();
        plugin.getLogger().severe("ONBOARDING_CLIENT_ACCEPTANCE_FAIL " + detail);
        plugin.getServer().getScheduler().runTask(plugin, plugin.getServer()::shutdown);
    }

    private static void writeMarker() throws IOException {
        String raw = System.getProperty(MARKER_PROPERTY, "").trim();
        if (raw.isEmpty()) {
            throw new IllegalStateException("onboarding acceptance marker path is missing");
        }
        Path marker = Path.of(raw).toAbsolutePath().normalize();
        Path parent = marker.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(marker, PASS_MARKER + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
