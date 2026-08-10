package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.transaction.JdbcValueTransactionService;
import com.branz.mmorpg.persistence.transaction.LotLocationMove;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.persistence.transaction.TransactionExecution;
import com.branz.mmorpg.persistence.transaction.TransactionRequest;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.util.Objects;
import java.util.UUID;

/** Durable full-stack lot movement between character inventory slots. */
final class PhysicalInventoryLotMoveService {
    private static final int STORAGE_SIZE = 36;

    private final DatabaseRuntime database;
    private final CharacterSessionService sessions;

    PhysicalInventoryLotMoveService(DatabaseRuntime database, CharacterSessionService sessions) {
        this.database = Objects.requireNonNull(database, "database");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> moveFullLot(
            LoadedCharacterSession session,
            LotId lotId,
            int sourceSlot,
            int destinationSlot,
            UUID operationId,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        if (!gameplayStorageSlot(sourceSlot)
                || !gameplayStorageSlot(destinationSlot)
                || sourceSlot == destinationSlot) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Full-lot move requires distinct non-Chronicle character inventory slots.");
        }
        ValueLocation source = ValueLocation.inventory("slot:" + sourceSlot);
        ValueLocation destination = ValueLocation.inventory("slot:" + destinationSlot);
        LotLocationRecord lot =
                session.snapshot().lotRecords().stream()
                        .filter(record -> record.lotId().equals(lotId))
                        .findFirst()
                        .orElse(null);
        if (lot == null
                || lot.ownerCharacterId().filter(session.characterId()::equals).isEmpty()
                || !lot.location().equals(source)
                || lot.quantity() < 1) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Lot no longer matches authoritative source slot truth.");
        }
        boolean destinationOccupied =
                session.snapshot().itemRecords().stream()
                                .anyMatch(record -> record.location().equals(destination))
                        || session.snapshot().lotRecords().stream()
                                .anyMatch(record -> record.location().equals(destination));
        if (destinationOccupied) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    "Full-lot move requires a database-empty destination slot; merge and swap are unsupported.");
        }

        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(operationId),
                        "physical-lot-move:" + operationId,
                        session.characterId(),
                        session.sessionId(),
                        JdbcValueTransactionService.LOT_MOVE,
                        "{\"lotId\":\""
                                + lotId.value()
                                + "\",\"version\":"
                                + lot.version()
                                + ",\"sourceSlot\":"
                                + sourceSlot
                                + "}",
                        "{\"destinationSlot\":" + destinationSlot + "}",
                        contentVersion);
        Result<TransactionExecution, TransactionErrorCode> moved =
                database.values()
                        .moveLot(
                                request,
                                new LotLocationMove(
                                        lot.lotId(),
                                        lot.version(),
                                        lot.ownerCharacterId(),
                                        lot.location(),
                                        lot.ownerCharacterId(),
                                        destination));
        if (moved instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    failure.error().code() + ": " + failure.detail());
        }
        return sessions.reload(session);
    }

    private static boolean gameplayStorageSlot(int slot) {
        return slot >= 0 && slot < STORAGE_SIZE && slot != ChronicleService.HOTBAR_SLOT;
    }
}
