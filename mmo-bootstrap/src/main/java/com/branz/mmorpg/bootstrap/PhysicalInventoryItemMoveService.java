package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.transaction.ItemLocationMove;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.JdbcValueTransactionService;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.persistence.transaction.TransactionExecution;
import com.branz.mmorpg.persistence.transaction.TransactionRequest;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import com.branz.mmorpg.persistence.transaction.ValueLocationType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Durable character-inventory move/swap boundary for one unique MMO item. */
final class PhysicalInventoryItemMoveService {
    private static final int LAST_STORAGE_SLOT = 35;

    private final DatabaseRuntime database;
    private final CharacterSessionService sessions;

    PhysicalInventoryItemMoveService(DatabaseRuntime database, CharacterSessionService sessions) {
        this.database = Objects.requireNonNull(database, "database");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> moveUniqueItem(
            LoadedCharacterSession session,
            ItemId itemId,
            int sourceSlot,
            int destinationSlot,
            UUID operationId,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        if (!validInventorySlot(sourceSlot) || !validInventorySlot(destinationSlot)) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Physical inventory move requires slots 0-35 excluding Chronicle slot 9.");
        }
        if (sourceSlot == destinationSlot) {
            return Result.success(session);
        }

        ValueLocation sourceLocation = ValueLocation.inventory("slot:" + sourceSlot);
        ValueLocation destinationLocation = ValueLocation.inventory("slot:" + destinationSlot);
        ItemLocationRecord source =
                session.snapshot().itemRecords().stream()
                        .filter(record -> record.itemId().equals(itemId))
                        .findFirst()
                        .orElse(null);
        if (source == null
                || source.ownerCharacterId().filter(session.characterId()::equals).isEmpty()
                || !source.location().equals(sourceLocation)) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Physical item source no longer matches authoritative inventory truth.");
        }
        boolean destinationHasLot =
                session.snapshot().lotRecords().stream()
                        .anyMatch(
                                record ->
                                        record.location().type()
                                                        == ValueLocationType.CHARACTER_INVENTORY
                                                && record.location().equals(destinationLocation));
        if (destinationHasLot) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    "Unique-item move cannot overwrite a commodity lot destination.");
        }
        ItemLocationRecord displaced =
                session.snapshot().itemRecords().stream()
                        .filter(record -> record.location().equals(destinationLocation))
                        .findFirst()
                        .orElse(null);
        if (displaced != null
                && displaced.ownerCharacterId().filter(session.characterId()::equals).isEmpty()) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Destination item is not owned by the active character.");
        }

        List<ItemLocationMove> moves = new ArrayList<>();
        moves.add(
                new ItemLocationMove(
                        source.itemId(),
                        source.version(),
                        source.ownerCharacterId(),
                        source.location(),
                        source.ownerCharacterId(),
                        destinationLocation));
        if (displaced != null) {
            moves.add(
                    new ItemLocationMove(
                            displaced.itemId(),
                            displaced.version(),
                            displaced.ownerCharacterId(),
                            displaced.location(),
                            displaced.ownerCharacterId(),
                            sourceLocation));
        }

        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(operationId),
                        "physical-inventory-item-move:" + operationId,
                        session.characterId(),
                        session.sessionId(),
                        moves.size() == 1
                                ? JdbcValueTransactionService.ITEM_MOVE
                                : JdbcValueTransactionService.ITEM_BATCH_MOVE,
                        "{\"itemId\":\""
                                + source.itemId().value()
                                + "\",\"sourceSlot\":"
                                + sourceSlot
                                + ",\"destinationSlot\":"
                                + destinationSlot
                                + ",\"sourceVersion\":"
                                + source.version()
                                + "}",
                        "{\"swapped\":" + (displaced != null) + "}",
                        contentVersion);
        Result<TransactionExecution, TransactionErrorCode> committed =
                moves.size() == 1
                        ? database.values().moveItem(request, moves.getFirst())
                        : database.values().moveItemsAtomically(request, moves);
        if (committed
                instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    failure.error().code() + ": " + failure.detail());
        }
        return sessions.reload(session);
    }

    private static boolean validInventorySlot(int slot) {
        return slot >= 0 && slot <= LAST_STORAGE_SLOT && slot != ChronicleService.HOTBAR_SLOT;
    }
}
