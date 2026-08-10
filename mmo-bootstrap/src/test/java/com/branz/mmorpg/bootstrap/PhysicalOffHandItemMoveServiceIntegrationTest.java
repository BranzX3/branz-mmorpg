package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.GuardCombatProfile;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ShieldProfile;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalOffHandItemMoveServiceIntegrationTest {
    private static final String CONTENT = "content.test.1";
    private static final DefinitionId SHIELD = DefinitionId.of("equipment.test.physical_shield");

    @Test
    void equipReplaySwapUnequipAndRestartPreserveExactShieldTruth(
            @org.junit.jupiter.api.io.TempDir Path databaseDirectory) throws Exception {
        DatabaseSettings settings = settings(databaseDirectory);
        UUID playerId = UUID.randomUUID();
        ItemId firstId;
        ItemId secondId;

        try (DatabaseRuntime database = DatabaseRuntime.start(settings)) {
            CharacterSessionService sessions = new CharacterSessionService(database);
            PhysicalOffHandItemMoveService offHand =
                    new PhysicalOffHandItemMoveService(database, sessions);
            LoadedCharacterSession opened = success(sessions.open(playerId));
            LoadedCharacterSession firstGranted =
                    success(sessions.grantTestValue(opened, shield(), 3, CONTENT));
            firstId = itemAt(firstGranted, ValueLocation.inventory("slot:3")).itemId();

            UUID equipOperation = UUID.randomUUID();
            LoadedCharacterSession equipped =
                    success(
                            offHand.swap(
                                    firstGranted,
                                    3,
                                    Optional.of(firstId),
                                    equipOperation,
                                    CONTENT));
            assertEquals(
                    firstId, equipped.snapshot().equipment().item(EquipmentSlot.OFF_HAND).orElseThrow());
            assertEquals(
                    ValueLocation.nativeEquipped("OFF_HAND"),
                    item(equipped, firstId).location());

            LoadedCharacterSession replayed =
                    success(
                            offHand.swap(
                                    firstGranted,
                                    3,
                                    Optional.of(firstId),
                                    equipOperation,
                                    CONTENT));
            assertEquals(
                    firstId, replayed.snapshot().equipment().item(EquipmentSlot.OFF_HAND).orElseThrow());
            assertEquals(item(equipped, firstId).version(), item(replayed, firstId).version());

            LoadedCharacterSession secondGranted =
                    success(sessions.grantTestValue(replayed, shield(), 4, CONTENT));
            secondId = itemAt(secondGranted, ValueLocation.inventory("slot:4")).itemId();
            LoadedCharacterSession swapped =
                    success(
                            offHand.swap(
                                    secondGranted,
                                    4,
                                    Optional.of(secondId),
                                    UUID.randomUUID(),
                                    CONTENT));
            assertEquals(
                    secondId, swapped.snapshot().equipment().item(EquipmentSlot.OFF_HAND).orElseThrow());
            assertEquals(ValueLocation.inventory("slot:4"), item(swapped, firstId).location());
            assertEquals(ValueLocation.nativeEquipped("OFF_HAND"), item(swapped, secondId).location());

            LoadedCharacterSession unequipped =
                    success(
                            offHand.swap(
                                    swapped,
                                    3,
                                    Optional.empty(),
                                    UUID.randomUUID(),
                                    CONTENT));
            assertTrue(unequipped.snapshot().equipment().item(EquipmentSlot.OFF_HAND).isEmpty());
            assertEquals(ValueLocation.inventory("slot:3"), item(unequipped, secondId).location());
            sessions.close(unequipped);
        }

        try (DatabaseRuntime restarted = DatabaseRuntime.start(settings)) {
            CharacterSessionService sessions = new CharacterSessionService(restarted);
            LoadedCharacterSession restored = success(sessions.open(playerId));
            assertTrue(restored.snapshot().equipment().item(EquipmentSlot.OFF_HAND).isEmpty());
            assertEquals(ValueLocation.inventory("slot:4"), item(restored, firstId).location());
            assertEquals(ValueLocation.inventory("slot:3"), item(restored, secondId).location());
            sessions.close(restored);
        }
    }

    @Test
    void chronicleAndCommoditySlotsFailClosed(@org.junit.jupiter.api.io.TempDir Path directory)
            throws Exception {
        try (DatabaseRuntime database = DatabaseRuntime.start(settings(directory))) {
            CharacterSessionService sessions = new CharacterSessionService(database);
            PhysicalOffHandItemMoveService offHand =
                    new PhysicalOffHandItemMoveService(database, sessions);
            LoadedCharacterSession session = success(sessions.open(UUID.randomUUID()));

            assertTrue(
                    offHand.swap(
                                    session,
                                    ChronicleService.HOTBAR_SLOT,
                                    Optional.empty(),
                                    UUID.randomUUID(),
                                    CONTENT)
                            instanceof Result.Failure<?, ?>);
            sessions.close(session);
        }
    }

    private static ItemDefinition shield() {
        return new ItemDefinition(
                SHIELD,
                SHIELD,
                ItemClass.UNIQUE_DURABLE,
                OptionalInt.of(180),
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(
                        new ShieldProfile(
                                new GuardCombatProfile(
                                        145,
                                        0.9,
                                        4,
                                        130,
                                        24,
                                        24,
                                        10,
                                        22,
                                        50))));
    }

    private static com.branz.mmorpg.persistence.transaction.ItemLocationRecord itemAt(
            LoadedCharacterSession session, ValueLocation location) {
        return session.snapshot().itemRecords().stream()
                .filter(record -> record.location().equals(location))
                .findFirst()
                .orElseThrow();
    }

    private static com.branz.mmorpg.persistence.transaction.ItemLocationRecord item(
            LoadedCharacterSession session, ItemId id) {
        return session.snapshot().itemRecords().stream()
                .filter(record -> record.itemId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static DatabaseSettings settings(Path directory) {
        return new DatabaseSettings(
                DatabaseMode.EMBEDDED_LOCAL,
                "LOCAL",
                directory,
                "",
                "",
                "",
                4,
                Duration.ofSeconds(5),
                true,
                Duration.ofSeconds(30),
                Duration.ofSeconds(10));
    }

    private static LoadedCharacterSession success(
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result) {
        if (result
                instanceof Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>
                        failure) {
            throw new AssertionError(failure.error().code() + ": " + failure.detail());
        }
        return ((Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>) result).value();
    }
}
