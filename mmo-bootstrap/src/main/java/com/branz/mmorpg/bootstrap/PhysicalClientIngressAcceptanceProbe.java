package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.input.InputRouteOutcome;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Test-only real-client acceptance for physical LMB -> Paper ingress -> primary coordinator. */
final class PhysicalClientIngressAcceptanceProbe {
    static final String ENABLE_PROPERTY = "mmo.bootstrap.physical-lmb-acceptance-test";
    static final String MARKER_PROPERTY = "mmo.bootstrap.physical-lmb-acceptance-marker";
    static final String PASS_MARKER = "PHYSICAL_LMB_INGRESS_ACCEPTANCE_PASS";
    private static final DefinitionId TRAINING_BLADE = DefinitionId.of("weapon.training_blade");

    private final JavaPlugin plugin;
    private final CharacterSessionController characters;
    private final ItemDefinition trainingBlade;
    private final String contentVersion;
    private UUID armedPlayer;
    private boolean completed;

    private PhysicalClientIngressAcceptanceProbe(
            JavaPlugin plugin,
            CharacterSessionController characters,
            ItemEngine items,
            String contentVersion) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.trainingBlade =
                Objects.requireNonNull(items, "items")
                        .find(TRAINING_BLADE)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "training blade definition missing"));
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
    }

    static void install(
            JavaPlugin plugin,
            CharacterSessionController characters,
            CombatSessionController combat,
            ItemEngine items,
            String contentVersion) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        PhysicalClientIngressAcceptanceProbe probe =
                new PhysicalClientIngressAcceptanceProbe(plugin, characters, items, contentVersion);
        characters.addReadyHandler(probe::onCharacterReady);
        combat.setPrimaryRouteObserver(probe::onPrimaryRouted);
        plugin.getServer().getScheduler().runTaskLater(plugin, probe::timeout, 20L * 120L);
        plugin.getLogger().info("PHYSICAL_LMB_INGRESS_ACCEPTANCE_ARMED");
    }

    private void onCharacterReady(Player player) {
        if (completed || armedPlayer != null) {
            return;
        }
        player.getInventory().setHeldItemSlot(0);
        Optional<ItemId> existing = findTrainingBlade(player);
        if (existing.isPresent()) {
            equip(player, existing.orElseThrow());
            return;
        }
        characters.grantTestValue(
                player,
                trainingBlade,
                contentVersion,
                result -> {
                    if (result
                            instanceof
                            Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>
                                    failure) {
                        fail("grant failed: " + failure.error().code() + " " + failure.detail());
                        return;
                    }
                    ItemId granted =
                            findTrainingBlade(player)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalStateException(
                                                            "granted training blade was not projected"));
                    equip(player, granted);
                });
    }

    private Optional<ItemId> findTrainingBlade(Player player) {
        return characters
                .active(player)
                .flatMap(
                        session ->
                                session.snapshot().itemRecords().stream()
                                        .filter(
                                                record ->
                                                        record.definitionId()
                                                                .equals(TRAINING_BLADE))
                                        .map(record -> record.itemId())
                                        .findFirst());
    }

    private void equip(Player player, ItemId itemId) {
        LoadedCharacterSession active =
                characters
                        .active(player)
                        .orElseThrow(
                                () -> new IllegalStateException("character session disappeared"));
        EquipmentLoadout desired =
                active.snapshot().equipment().with(EquipmentSlot.MAIN_HAND, Optional.of(itemId));
        characters.commitEquipment(
                player,
                desired,
                contentVersion,
                result -> {
                    if (result
                            instanceof
                            Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>
                                    failure) {
                        fail("equip failed: " + failure.error().code() + " " + failure.detail());
                        return;
                    }
                    player.getInventory().setHeldItemSlot(0);
                    armedPlayer = player.getUniqueId();
                    plugin.getLogger().info("PHYSICAL_LMB_INGRESS_ACCEPTANCE_PLAYER_READY");
                });
    }

    private void onPrimaryRouted(Player player, InputRouteOutcome outcome) {
        if (completed || armedPlayer == null || !armedPlayer.equals(player.getUniqueId())) {
            return;
        }
        completed = true;
        try {
            writeMarker();
            plugin.getLogger().info(PASS_MARKER + " outcome=" + outcome);
        } catch (IOException exception) {
            fail("marker write failed: " + exception.getMessage());
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, plugin.getServer()::shutdown, 2L);
    }

    private void timeout() {
        if (!completed) {
            fail("timed out before a physical LMB reached PrimaryAttackInputCoordinator");
        }
    }

    private void fail(String detail) {
        if (completed) {
            return;
        }
        completed = true;
        plugin.getLogger().severe("PHYSICAL_LMB_INGRESS_ACCEPTANCE_FAIL " + detail);
        plugin.getServer().getScheduler().runTask(plugin, plugin.getServer()::shutdown);
    }

    private static void writeMarker() throws IOException {
        String raw = System.getProperty(MARKER_PROPERTY, "").trim();
        if (raw.isEmpty()) {
            throw new IllegalStateException("physical LMB acceptance marker path is missing");
        }
        Path marker = Path.of(raw).toAbsolutePath().normalize();
        Path parent = marker.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(marker, PASS_MARKER + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
