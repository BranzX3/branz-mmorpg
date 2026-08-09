package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Real-client acceptance for first choice -> durable kit -> reconnect -> first combat hit. */
final class OnboardingClientAcceptanceProbe {
    static final String ENABLE_PROPERTY = "mmo.bootstrap.onboarding-acceptance-test";
    static final String MARKER_PROPERTY = "mmo.bootstrap.onboarding-acceptance-marker";
    static final String PASS_MARKER = "ONBOARDING_CLIENT_ACCEPTANCE_PASS";
    private static final String TRAINING_DUMMY_TAG = "branzmmo.training_dummy";
    private static final DefinitionId GREATSWORD = DefinitionId.of("weapon.training_greatsword");
    private static final DefinitionId GREATSWORD_MOVE =
            DefinitionId.of("move.training_greatsword.committed_cleave");

    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private UUID playerId;
    private UUID trainingDummyId;
    private int readyCount;
    private boolean checking;
    private boolean completed;

    private OnboardingClientAcceptanceProbe(
            JavaPlugin plugin, CharacterSessionController characters) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
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
        plugin.getLogger().info("ONBOARDING_CLIENT_ACCEPTANCE_ARMED");
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
        spawnTrainingDummy(player);
        plugin.getLogger().info("ONBOARDING_CLIENT_ACCEPTANCE_RECONNECT_READY");
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
        Location target = origin.add(forward.multiply(1.75));
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
                                + Math.sqrt(dummy.getLocation().distanceSquared(player.getLocation())));
    }

    private void onSuccessfulCombatAction(
            CharacterId actorId, UUID actionId, DefinitionId moveId, long currentTick) {
        if (completed || readyCount != 2 || playerId == null || !actorId.value().equals(playerId)) {
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
        try {
            writeMarker();
            completed = true;
            plugin.getLogger()
                    .info(
                            PASS_MARKER
                                    + " move="
                                    + moveId.value()
                                    + " action="
                                    + actionId
                                    + " tick="
                                    + currentTick);
        } catch (IOException exception) {
            fail("marker write failed: " + exception.getMessage());
            return;
        }
        removeTrainingDummy();
        plugin.getServer().getScheduler().runTaskLater(plugin, plugin.getServer()::shutdown, 2L);
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

    private void timeout() {
        if (!completed) {
            fail("timed out before onboarding landed the first successful combat action");
        }
    }

    private void fail(String detail) {
        if (completed) {
            return;
        }
        completed = true;
        removeTrainingDummy();
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
