package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalInventoryLotMoveServiceIntegrationTest {
    private static final String CONTENT = "content.test.1";
    private static final DefinitionId CONSUMABLE = DefinitionId.of("consumable.test.physical_lot");

    @Test
    void fullLotMoveReplaysExactlyAndSurvivesDatabaseRestart(
            @org.junit.jupiter.api.io.TempDir Path databaseDirectory) throws Exception {
        DatabaseSettings settings = settings(databaseDirectory);
        UUID playerId = UUID.randomUUID();
        LotId lotId;

        try (DatabaseRuntime database = DatabaseRuntime.start(settings)) {
            CharacterSessionService sessions = new CharacterSessionService(database);
            PhysicalInventoryLotMoveService moves =
                    new PhysicalInventoryLotMoveService(database, sessions);
            LoadedCharacterSession opened = success(sessions.open(playerId));
            LoadedCharacterSession granted =
                    success(sessions.grantTestValue(opened, lotDefinition(), 12, 7, CONTENT));
            lotId = lotAt(granted, ValueLocation.inventory("slot:12")).lotId();

            UUID operationId = UUID.randomUUID();
            LoadedCharacterSession moved =
                    success(moves.moveFullLot(granted, lotId, 12, 3, operationId, CONTENT));
            assertEquals(7, lot(moved, lotId).quantity());
            assertEquals(ValueLocation.inventory("slot:3"), lot(moved, lotId).location());

            LoadedCharacterSession replayed =
                    success(moves.moveFullLot(granted, lotId, 12, 3, operationId, CONTENT));
            assertEquals(ValueLocation.inventory("slot:3"), lot(replayed, lotId).location());
            assertEquals(lot(moved, lotId).version(), lot(replayed, lotId).version());
            sessions.close(replayed);
        }

        try (DatabaseRuntime restarted = DatabaseRuntime.start(settings)) {
            CharacterSessionService sessions = new CharacterSessionService(restarted);
            LoadedCharacterSession restored = success(sessions.open(playerId));
            assertEquals(ValueLocation.inventory("slot:3"), lot(restored, lotId).location());
            assertEquals(7, lot(restored, lotId).quantity());
            sessions.close(restored);
        }
    }

    @Test
    void occupiedAndChronicleDestinationsFailClosed(
            @org.junit.jupiter.api.io.TempDir Path databaseDirectory) throws Exception {
        try (DatabaseRuntime database = DatabaseRuntime.start(settings(databaseDirectory))) {
            CharacterSessionService sessions = new CharacterSessionService(database);
            PhysicalInventoryLotMoveService moves =
                    new PhysicalInventoryLotMoveService(database, sessions);
            LoadedCharacterSession opened = success(sessions.open(UUID.randomUUID()));
            LoadedCharacterSession first =
                    success(sessions.grantTestValue(opened, lotDefinition(), 12, 7, CONTENT));
            LoadedCharacterSession second =
                    success(sessions.grantTestValue(first, lotDefinition(), 3, 2, CONTENT));
            LotId firstId = lotAt(second, ValueLocation.inventory("slot:12")).lotId();

            assertTrue(
                    moves.moveFullLot(
                                    second,
                                    firstId,
                                    12,
                                    3,
                                    UUID.randomUUID(),
                                    CONTENT)
                            instanceof Result.Failure<?, ?>);
            assertTrue(
                    moves.moveFullLot(
                                    second,
                                    firstId,
                                    12,
                                    ChronicleService.HOTBAR_SLOT,
                                    UUID.randomUUID(),
                                    CONTENT)
                            instanceof Result.Failure<?, ?>);
            sessions.close(second);
        }
    }

    private static ItemDefinition lotDefinition() {
        return new ItemDefinition(
                CONSUMABLE,
                CONSUMABLE,
                ItemClass.STACKABLE_LOT,
                OptionalInt.empty(),
                false);
    }

    private static com.branz.mmorpg.persistence.transaction.LotLocationRecord lotAt(
            LoadedCharacterSession session, ValueLocation location) {
        return session.snapshot().lotRecords().stream()
                .filter(record -> record.location().equals(location))
                .findFirst()
                .orElseThrow();
    }

    private static com.branz.mmorpg.persistence.transaction.LotLocationRecord lot(
            LoadedCharacterSession session, LotId id) {
        return session.snapshot().lotRecords().stream()
                .filter(record -> record.lotId().equals(id))
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
