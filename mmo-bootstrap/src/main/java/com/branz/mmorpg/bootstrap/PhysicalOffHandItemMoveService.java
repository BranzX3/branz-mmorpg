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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Atomic character-inventory ↔ native OFF_HAND movement for one physical shield interaction. */
final class PhysicalOffHandItemMoveService {
    private final DatabaseRuntime database;
    private final CharacterSessionService sessions;

    PhysicalOffHandItemMoveService(DatabaseRuntime database, CharacterSessionService sessions) {
        this.database = Objects.requireNonNull(database, "database");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> swap(
            LoadedCharacterSession session,
            int selectedSlot,
            Optional<ItemId> selectedShieldItemId,
            UUID operationId,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(selectedShieldItemId, "selectedShieldItemId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        if (selectedSlot < 0 || selectedSlot >= ChronicleService.HOTBAR_SLOT) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Physical off-hand swap requires one gameplay hotbar slot 1-8.");
        }

        ValueLocation selectedLocation = ValueLocation.inventory("slot:" + selectedSlot);
        if (session.snapshot().lotRecords().stream()
                .anyMatch(record -> record.location().equals(selectedLocation))) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    "Physical off-hand swap cannot overwrite or move a commodity lot.");
        }

        ItemLocationRecord selected =
                session.snapshot().itemRecords().stream()
                        .filter(record -> record.location().equals(selectedLocation))
                        .findFirst()
                        .orElse(null);
        if (selectedShieldItemId.isPresent()) {
            if (selected == null
                    || !selected.itemId().equals(selectedShieldItemId.orElseThrow())
                    || selected.ownerCharacterId().filter(session.characterId()::equals).isEmpty()) {
                return Result.failure(
                        CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                        "Selected shield no longer matches authoritative hotbar truth.");
            }
        } else if (selected != null) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    "Selected hotbar slot contains a unique item that is not an eligible shield.");
        }

        ValueLocation offHandLocation = ValueLocation.nativeEquipped(EquipmentSlot.OFF_HAND.name());
        ItemId offHandId = session.snapshot().equipment().item(EquipmentSlot.OFF_HAND).orElse(null);
        ItemLocationRecord offHand =
                offHandId == null
                        ? null
                        : session.snapshot().itemRecords().stream()
                                .filter(record -> record.itemId().equals(offHandId))
                                .findFirst()
                                .orElse(null);
        if (offHandId != null
                && (offHand == null
                        || offHand.ownerCharacterId().filter(session.characterId()::equals).isEmpty()
                        || !offHand.location().equals(offHandLocation))) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Committed OFF_HAND item does not match authoritative item location truth.");
        }
        if (selected == null && offHand == null) {
            return Result.success(session);
        }

        List<ItemLocationMove> moves = new ArrayList<>();
        if (selected != null) {
            moves.add(
                    new ItemLocationMove(
                            selected.itemId(),
                            selected.version(),
                            selected.ownerCharacterId(),
                            selected.location(),
                            selected.ownerCharacterId(),
                            offHandLocation));
        }
        if (offHand != null) {
            moves.add(
                    new ItemLocationMove(
                            offHand.itemId(),
                            offHand.version(),
                            offHand.ownerCharacterId(),
                            offHand.location(),
                            offHand.ownerCharacterId(),
                            selectedLocation));
        }

        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(operationId),
                        "physical-off-hand-swap:" + operationId,
                        session.characterId(),
                        session.sessionId(),
                        moves.size() == 1
                                ? JdbcValueTransactionService.ITEM_MOVE
                                : JdbcValueTransactionService.ITEM_BATCH_MOVE,
                        "{\"selectedSlot\":"
                                + selectedSlot
                                + ",\"incomingItemId\":\""
                                + (selected == null ? "" : selected.itemId().value())
                                + "\",\"outgoingItemId\":\""
                                + (offHand == null ? "" : offHand.itemId().value())
                                + "\"}",
                        "{\"nativeSlot\":\"OFF_HAND\"}",
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
}
