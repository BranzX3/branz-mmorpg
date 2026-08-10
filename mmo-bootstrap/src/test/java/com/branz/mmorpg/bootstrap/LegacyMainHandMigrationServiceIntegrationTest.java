package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LegacyMainHandMigrationServiceIntegrationTest {
    private static final String CONTENT = "content.test.1";
    private static final DefinitionId SWORD = DefinitionId.of("weapon.test.legacy_main_hand");

    @Test
    void migrationPreservesUuidPayloadReplayAndDatabaseRestart(
            @org.junit.jupiter.api.io.TempDir Path databaseDirectory) throws Exception {
        DatabaseSettings settings = settings(databaseDirectory);
        UUID playerId = UUID.randomUUID();
        ItemId swordId;
        String payload;

        try (DatabaseRuntime database = DatabaseRuntime.start(settings)) {
            CharacterSessionService sessions = new CharacterSessionService(database);
            LegacyMainHandMigrationService migration =
                    new LegacyMainHandMigrationService(database, sessions);
            LoadedCharacterSession opened = success(sessions.open(playerId));
            LoadedCharacterSession granted =
                    success(sessions.grantTestValue(opened, uniqueSword(), 12, CONTENT));
            swordId = itemAt(granted, ValueLocation.inventory("slot:12")).itemId();
            LoadedCharacterSession equipped =
                    success(
                            sessions.commitEquipment(
                                    granted,
                                    granted
                                            .snapshot()
                                            .equipment()
                                            .with(
                                                    EquipmentSlot.MAIN_HAND,
                                                    java.util.Optional.of(swordId)),
                                    CONTENT));
            ItemLocationRecord legacy =
                    itemAt(equipped, ValueLocation.nativeEquipped("MAIN_HAND"));
            payload = legacy.payloadJson();

            LoadedCharacterSession migrated = success(migration.migrate(equipped, CONTENT));
            assertTrue(migrated.snapshot().equipment().item(EquipmentSlot.MAIN_HAND).isEmpty());
            ItemLocationRecord physical = itemAt(migrated, ValueLocation.inventory("slot:0"));
            assertEquals(swordId, physical.itemId());
            assertEquals(payload, physical.payloadJson());
            assertEquals(legacy.version() + 1, physical.version());

            LoadedCharacterSession replayed = success(migration.migrate(equipped, CONTENT));
            ItemLocationRecord replayedPhysical =
                    itemAt(replayed, ValueLocation.inventory("slot:0"));
            assertEquals(swordId, replayedPhysical.itemId());
            assertEquals(physical.version(), replayedPhysical.version());
            assertTrue(replayed.snapshot().equipment().item(EquipmentSlot.MAIN_HAND).isEmpty());
            sessions.close(replayed);
        }

        try (DatabaseRuntime restarted = DatabaseRuntime.start(settings)) {
            CharacterSessionService sessions = new CharacterSessionService(restarted);
            LegacyMainHandMigrationService migration =
                    new LegacyMainHandMigrationService(restarted, sessions);
            LoadedCharacterSession restored = success(sessions.open(playerId));
            assertTrue(restored.snapshot().equipment().item(EquipmentSlot.MAIN_HAND).isEmpty());
            ItemLocationRecord physical = itemAt(restored, ValueLocation.inventory("slot:0"));
            assertEquals(swordId, physical.itemId());
            assertEquals(payload, physical.payloadJson());

            LoadedCharacterSession noOp = success(migration.migrate(restored, CONTENT));
            assertEquals(physical.version(), itemAt(noOp, ValueLocation.inventory("slot:0")).version());
            assertFalse(noOp.snapshot().inventory().isEmpty());
            sessions.close(noOp);
        }
    }

    private static ItemDefinition uniqueSword() {
        return new ItemDefinition(SWORD, SWORD, ItemClass.UNIQUE_DURABLE, OptionalInt.of(100), false);
    }

    private static ItemLocationRecord itemAt(
            LoadedCharacterSession session, ValueLocation location) {
        return session.snapshot().itemRecords().stream()
                .filter(record -> record.location().equals(location))
                .findFirst()
                .orElseThrow();
    }

    private static DatabaseSettings settings(Path databaseDirectory) {
        return new DatabaseSettings(
                DatabaseMode.EMBEDDED_LOCAL,
                "LOCAL",
                databaseDirectory,
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
        if (!result.isSuccess()) {
            Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode> failure =
                    (Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>) result;
            throw new AssertionError(failure.error().code() + ": " + failure.detail());
        }
        return ((Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>) result).value();
    }
}
