package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.persistence.transaction.ItemLocationMove;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.JdbcValueTransactionService;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.persistence.transaction.TransactionExecution;
import com.branz.mmorpg.persistence.transaction.TransactionRequest;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Reconciles the retired persistent MAIN_HAND location back into character inventory. */
final class LegacyMainHandMigrationService {
    private static final int INVENTORY_SIZE = 36;

    private final DatabaseRuntime database;
    private final CharacterSessionService sessions;

    LegacyMainHandMigrationService(DatabaseRuntime database, CharacterSessionService sessions) {
        this.database = Objects.requireNonNull(database, "database");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> migrate(
            LoadedCharacterSession session, String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(contentVersion, "contentVersion");
        ItemId legacyItemId =
                session.snapshot().equipment().item(EquipmentSlot.MAIN_HAND).orElse(null);
        if (legacyItemId == null) {
            return Result.success(session);
        }
        ValueLocation legacyLocation = ValueLocation.nativeEquipped(EquipmentSlot.MAIN_HAND.name());
        ItemLocationRecord legacy =
                session.snapshot().itemRecords().stream()
                        .filter(record -> record.itemId().equals(legacyItemId))
                        .findFirst()
                        .orElse(null);
        if (legacy == null
                || legacy.ownerCharacterId().filter(session.characterId()::equals).isEmpty()
                || !legacy.location().equals(legacyLocation)) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Legacy MAIN_HAND equipment does not match authoritative item truth.");
        }
        int destinationSlot = firstFreeInventorySlot(session.snapshot());
        if (destinationSlot < 0) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    "Legacy MAIN_HAND migration requires one free character inventory slot.");
        }
        UUID operationId = migrationOperationId(session, legacyItemId);
        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(operationId),
                        "legacy-main-hand-migration:" + legacyItemId.value(),
                        session.characterId(),
                        session.sessionId(),
                        JdbcValueTransactionService.ITEM_MOVE,
                        "{\"itemId\":\""
                                + legacyItemId.value()
                                + "\",\"version\":"
                                + legacy.version()
                                + ",\"from\":\"NATIVE_EQUIPPED/MAIN_HAND\"}",
                        "{\"inventorySlot\":" + destinationSlot + "}",
                        contentVersion);
        Result<TransactionExecution, TransactionErrorCode> migrated =
                database.values()
                        .moveItem(
                                request,
                                new ItemLocationMove(
                                        legacy.itemId(),
                                        legacy.version(),
                                        legacy.ownerCharacterId(),
                                        legacy.location(),
                                        legacy.ownerCharacterId(),
                                        ValueLocation.inventory("slot:" + destinationSlot)));
        if (migrated
                instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    failure.error().code() + ": " + failure.detail());
        }
        return sessions.reload(session);
    }

    private static UUID migrationOperationId(
            LoadedCharacterSession session, ItemId legacyMainHandItemId) {
        String key =
                "legacy-main-hand:"
                        + session.characterId().value()
                        + ":"
                        + legacyMainHandItemId.value();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static int firstFreeInventorySlot(PersistentCharacterSnapshot snapshot) {
        boolean[] occupied = new boolean[INVENTORY_SIZE];
        occupied[ChronicleService.HOTBAR_SLOT] = true;
        snapshot.inventory().forEach(projection -> occupied[projection.slot()] = true);
        for (int slot = 0; slot < occupied.length; slot++) {
            if (!occupied[slot]) {
                return slot;
            }
        }
        return -1;
    }
}
