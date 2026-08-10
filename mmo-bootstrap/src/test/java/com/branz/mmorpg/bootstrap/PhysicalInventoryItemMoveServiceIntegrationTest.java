package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalInventoryItemMoveServiceIntegrationTest {
    private static final String CONTENT = "content.test.1";
    private static final DefinitionId SWORD = DefinitionId.of("weapon.test.physical_sword");
    private static final DefinitionId CHARM = DefinitionId.of("item.test.physical_charm");

    @Test
    void moveSwapReplayAndRestartPreserveExactUniqueItemIdentity(
            @org.junit.jupiter.api.io.TempDir Path databaseDirectory) throws Exception {
        DatabaseSettings settings = settings(databaseDirectory);
        UUID playerId = UUID.randomUUID();
        UUID moveOperation = UUID.randomUUID();
        UUID swapOperation = UUID.randomUUID();
        ItemId swordId;
        ItemId charmId;

        try (DatabaseRuntime database = DatabaseRuntime.start(settings)) {
            CharacterSessionService sessions = new CharacterSessionService(database);
            PhysicalInventoryItemMoveService moves =
                    new PhysicalInventoryItemMoveService(database, sessions);
            LoadedCharacterSession opened = success(sessions.open(playerId));
            LoadedCharacterSession withSword =
                    success(sessions.grantTestValue(opened, unique(SWORD), 12, CONTENT));
            LoadedCharacterSession withBoth =
                    success(sessions.grantTestValue(withSword, unique(CHARM), 5, CONTENT));
            swordId = itemAt(withBoth, 12).itemId();
            charmId = itemAt(withBoth, 5).itemId();

            LoadedCharacterSession moved =
                    success(moves.moveUniqueItem(withBoth, swordId, 12, 3, moveOperation, CONTENT));
            assertEquals(swordId, itemAt(moved, 3).itemId());
            assertEquals(charmId, itemAt(moved, 5).itemId());

            LoadedCharacterSession replayed =
                    success(moves.moveUniqueItem(withBoth, swordId, 12, 3, moveOperation, CONTENT));
            assertEquals(swordId, itemAt(replayed, 3).itemId());
            assertEquals(charmId, itemAt(replayed, 5).itemId());

            LoadedCharacterSession swapped =
                    success(moves.moveUniqueItem(replayed, swordId, 3, 5, swapOperation, CONTENT));
            assertEquals(swordId, itemAt(swapped, 5).itemId());
            assertEquals(charmId, itemAt(swapped, 3).itemId());
            sessions.close(swapped);
        }

        try (DatabaseRuntime restarted = DatabaseRuntime.start(settings)) {
            CharacterSessionService sessions = new CharacterSessionService(restarted);
            LoadedCharacterSession restored = success(sessions.open(playerId));
            assertEquals(swordId, itemAt(restored, 5).itemId());
            assertEquals(charmId, itemAt(restored, 3).itemId());
            assertTrue(
                    restored.snapshot().equipment().equipped().values().stream()
                            .noneMatch(id -> id.equals(swordId)));
            sessions.close(restored);
        }
    }

    @Test
    void chronicleSlotIsRejectedWithoutMutation(
            @org.junit.jupiter.api.io.TempDir Path databaseDirectory) throws Exception {
        try (DatabaseRuntime database = DatabaseRuntime.start(settings(databaseDirectory))) {
            CharacterSessionService sessions = new CharacterSessionService(database);
            PhysicalInventoryItemMoveService moves =
                    new PhysicalInventoryItemMoveService(database, sessions);
            LoadedCharacterSession opened = success(sessions.open(UUID.randomUUID()));
            LoadedCharacterSession granted =
                    success(sessions.grantTestValue(opened, unique(SWORD), 10, CONTENT));
            ItemId swordId = itemAt(granted, 10).itemId();

            Result<LoadedCharacterSession, CharacterSessionErrorCode> rejected =
                    moves.moveUniqueItem(
                            granted,
                            swordId,
                            10,
                            ChronicleService.HOTBAR_SLOT,
                            UUID.randomUUID(),
                            CONTENT);

            assertTrue(rejected instanceof Result.Failure<?, ?>);
            LoadedCharacterSession reloaded = success(sessions.reload(granted));
            assertEquals(swordId, itemAt(reloaded, 10).itemId());
            sessions.close(reloaded);
        }
    }

    private static ItemDefinition unique(DefinitionId id) {
        return new ItemDefinition(id, id, ItemClass.UNIQUE_DURABLE, OptionalInt.empty(), false);
    }

    private static ItemLocationRecord itemAt(LoadedCharacterSession session, int slot) {
        ValueLocation location = ValueLocation.inventory("slot:" + slot);
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
