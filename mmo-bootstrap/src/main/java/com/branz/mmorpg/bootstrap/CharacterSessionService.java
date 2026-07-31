package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.identity.SessionId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.persistence.lease.CharacterLease;
import com.branz.mmorpg.persistence.lease.LeaseAcquireOutcome;
import com.branz.mmorpg.persistence.lease.LeaseErrorCode;
import com.branz.mmorpg.persistence.transaction.ItemLocationMove;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.JdbcValueTransactionService;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.NewItemLocation;
import com.branz.mmorpg.persistence.transaction.NewLotLocation;
import com.branz.mmorpg.persistence.transaction.ReconciliationErrorCode;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.persistence.transaction.TransactionExecution;
import com.branz.mmorpg.persistence.transaction.TransactionRequest;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Blocking session/lease aggregate. Callers must run every method off the Paper thread. */
final class CharacterSessionService {
    private final DatabaseRuntime database;

    CharacterSessionService(DatabaseRuntime database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> open(UUID authenticatedPlayerId) {
        Objects.requireNonNull(authenticatedPlayerId, "authenticatedPlayerId");
        CharacterId characterId = new CharacterId(authenticatedPlayerId);
        SessionId sessionId = new SessionId(UUID.randomUUID());
        Result<LeaseAcquireOutcome, LeaseErrorCode> acquired =
                database.leases()
                        .acquire(
                                characterId,
                                database.serverInstanceId(),
                                sessionId,
                                database.settings().leaseTtl());
        if (acquired instanceof Result.Failure<LeaseAcquireOutcome, LeaseErrorCode> failure) {
            return persistenceFailure(failure.error(), failure.detail());
        }
        LeaseAcquireOutcome outcome =
                ((Result.Success<LeaseAcquireOutcome, LeaseErrorCode>) acquired).value();
        CharacterLease lease;
        if (outcome instanceof LeaseAcquireOutcome.Conflict conflict) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_LEASE_CONFLICT,
                    "Character is active on another server session until "
                            + conflict.lease().expiresAt());
        } else if (outcome instanceof LeaseAcquireOutcome.RecoveryRequired recovery) {
            Result<?, ReconciliationErrorCode> scan =
                    database.reconciliation().scan(Duration.ofSeconds(1), 1000);
            if (scan instanceof Result.Failure<?, ReconciliationErrorCode> failure) {
                return persistenceFailure(failure.error(), failure.detail());
            }
            Result<CharacterLease, LeaseErrorCode> recovered =
                    database.leases()
                            .recoverExpired(
                                    characterId,
                                    recovery.lease().version(),
                                    database.serverInstanceId(),
                                    sessionId,
                                    database.settings().leaseTtl());
            if (recovered instanceof Result.Failure<CharacterLease, LeaseErrorCode> failure) {
                return persistenceFailure(failure.error(), failure.detail());
            }
            lease = ((Result.Success<CharacterLease, LeaseErrorCode>) recovered).value();
        } else {
            lease = outcome.lease();
        }

        Result<List<ItemLocationRecord>, TransactionErrorCode> itemRows =
                database.values().findItemsOwnedBy(characterId);
        if (itemRows
                instanceof Result.Failure<List<ItemLocationRecord>, TransactionErrorCode> failure) {
            releaseQuietly(characterId, sessionId, lease);
            return persistenceFailure(failure.error(), failure.detail());
        }
        Result<List<LotLocationRecord>, TransactionErrorCode> lotRows =
                database.values().findLotsOwnedBy(characterId);
        if (lotRows
                instanceof Result.Failure<List<LotLocationRecord>, TransactionErrorCode> failure) {
            releaseQuietly(characterId, sessionId, lease);
            return persistenceFailure(failure.error(), failure.detail());
        }
        try {
            PersistentCharacterSnapshot snapshot =
                    PersistentCharacterSnapshotMapper.map(
                            ((Result.Success<List<ItemLocationRecord>, TransactionErrorCode>)
                                            itemRows)
                                    .value(),
                            ((Result.Success<List<LotLocationRecord>, TransactionErrorCode>)
                                            lotRows)
                                    .value());
            return Result.success(
                    new LoadedCharacterSession(characterId, sessionId, lease, snapshot));
        } catch (IllegalArgumentException exception) {
            releaseQuietly(characterId, sessionId, lease);
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID, exception.getMessage());
        }
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> heartbeat(
            LoadedCharacterSession session) {
        Objects.requireNonNull(session, "session");
        Result<CharacterLease, LeaseErrorCode> result =
                database.leases()
                        .heartbeat(
                                session.characterId(),
                                database.serverInstanceId(),
                                session.sessionId(),
                                session.lease().version(),
                                database.settings().leaseTtl());
        if (result instanceof Result.Failure<CharacterLease, LeaseErrorCode> failure) {
            return persistenceFailure(failure.error(), failure.detail());
        }
        return Result.success(
                session.withLease(
                        ((Result.Success<CharacterLease, LeaseErrorCode>) result).value()));
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> grantTestValue(
            LoadedCharacterSession session,
            ItemDefinition definition,
            int inventorySlot,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(contentVersion, "contentVersion");
        UUID valueId = UUID.randomUUID();
        String provenance = "dev:" + session.characterId().value();
        String payload = "{\"displayRevision\":1,\"testProvenance\":\"" + provenance + "\"}";
        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(UUID.randomUUID()),
                        "dev-grant:" + valueId,
                        session.characterId(),
                        session.sessionId(),
                        definition.itemClass() == ItemClass.UNIQUE_DURABLE
                                ? JdbcValueTransactionService.ITEM_GRANT
                                : JdbcValueTransactionService.LOT_GRANT,
                        "{}",
                        "{\"definitionId\":\"" + definition.id().value() + "\"}",
                        contentVersion);
        Result<TransactionExecution, TransactionErrorCode> granted;
        if (definition.itemClass() == ItemClass.UNIQUE_DURABLE) {
            granted =
                    database.values()
                            .grantItem(
                                    request,
                                    new NewItemLocation(
                                            new ItemId(valueId),
                                            definition.id(),
                                            java.util.Optional.of(session.characterId()),
                                            ValueLocation.inventory("slot:" + inventorySlot),
                                            payload));
        } else {
            granted =
                    database.values()
                            .grantLot(
                                    request,
                                    new NewLotLocation(
                                            new LotId(valueId),
                                            definition.id(),
                                            "dev-test",
                                            1,
                                            java.util.Optional.of(session.characterId()),
                                            ValueLocation.inventory("slot:" + inventorySlot),
                                            payload));
        }
        if (granted instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    failure.error().code() + ": " + failure.detail());
        }
        return reload(session);
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> commitEquipment(
            LoadedCharacterSession session, EquipmentLoadout desired, String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(desired, "desired");
        Objects.requireNonNull(contentVersion, "contentVersion");
        List<EquipmentSlot> changed =
                java.util.Arrays.stream(EquipmentSlot.values())
                        .filter(
                                slot ->
                                        !session.snapshot()
                                                .equipment()
                                                .item(slot)
                                                .equals(desired.item(slot)))
                        .toList();
        if (changed.isEmpty()) {
            return Result.success(session);
        }
        if (changed.size() != 1) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    "This Scene slice commits one equipment slot at a time.");
        }
        EquipmentSlot slot = changed.getFirst();
        Optional<ItemId> desiredItem = desired.item(slot);
        if (desiredItem.isEmpty()) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    "Unequip requires an explicit free destination slot.");
        }
        ItemLocationRecord incoming =
                findItem(session.snapshot().itemRecords(), desiredItem.orElseThrow()).orElse(null);
        if (incoming == null
                || incoming.location().type()
                        != com.branz.mmorpg.persistence.transaction.ValueLocationType
                                .CHARACTER_INVENTORY) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    "Selected item is not in this character's inventory.");
        }
        ValueLocation equippedLocation =
                isNative(slot)
                        ? ValueLocation.nativeEquipped(slot.name())
                        : ValueLocation.virtualEquipped(slot.name());
        List<ItemLocationMove> moves = new java.util.ArrayList<>();
        moves.add(
                new ItemLocationMove(
                        incoming.itemId(),
                        incoming.version(),
                        incoming.ownerCharacterId(),
                        incoming.location(),
                        incoming.ownerCharacterId(),
                        equippedLocation));

        Optional<ItemId> currentItem = session.snapshot().equipment().item(slot);
        if (currentItem.isPresent()) {
            ItemLocationRecord displaced =
                    findItem(session.snapshot().itemRecords(), currentItem.orElseThrow())
                            .orElse(null);
            if (displaced == null || !displaced.location().equals(equippedLocation)) {
                return Result.failure(
                        CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                        "Committed equipment row does not match the Scene loadout.");
            }
            moves.add(
                    new ItemLocationMove(
                            displaced.itemId(),
                            displaced.version(),
                            displaced.ownerCharacterId(),
                            displaced.location(),
                            displaced.ownerCharacterId(),
                            incoming.location()));
        }
        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(UUID.randomUUID()),
                        "scene-equip:" + session.sessionId().value() + ":" + UUID.randomUUID(),
                        session.characterId(),
                        session.sessionId(),
                        JdbcValueTransactionService.ITEM_BATCH_MOVE,
                        "{\"slot\":\"" + slot.name() + "\"}",
                        "{\"itemId\":\"" + incoming.itemId().value() + "\"}",
                        contentVersion);
        Result<TransactionExecution, TransactionErrorCode> committed =
                database.values().moveItemsAtomically(request, moves);
        if (committed
                instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    failure.error().code() + ": " + failure.detail());
        }
        return reload(session);
    }

    void close(LoadedCharacterSession session) {
        Objects.requireNonNull(session, "session");
        releaseQuietly(session.characterId(), session.sessionId(), session.lease());
    }

    private Result<LoadedCharacterSession, CharacterSessionErrorCode> reload(
            LoadedCharacterSession session) {
        Result<List<ItemLocationRecord>, TransactionErrorCode> itemRows =
                database.values().findItemsOwnedBy(session.characterId());
        if (itemRows
                instanceof Result.Failure<List<ItemLocationRecord>, TransactionErrorCode> failure) {
            return persistenceFailure(failure.error(), failure.detail());
        }
        Result<List<LotLocationRecord>, TransactionErrorCode> lotRows =
                database.values().findLotsOwnedBy(session.characterId());
        if (lotRows
                instanceof Result.Failure<List<LotLocationRecord>, TransactionErrorCode> failure) {
            return persistenceFailure(failure.error(), failure.detail());
        }
        try {
            PersistentCharacterSnapshot snapshot =
                    PersistentCharacterSnapshotMapper.map(
                            ((Result.Success<List<ItemLocationRecord>, TransactionErrorCode>)
                                            itemRows)
                                    .value(),
                            ((Result.Success<List<LotLocationRecord>, TransactionErrorCode>)
                                            lotRows)
                                    .value());
            return Result.success(
                    new LoadedCharacterSession(
                            session.characterId(), session.sessionId(), session.lease(), snapshot));
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID, exception.getMessage());
        }
    }

    private static Optional<ItemLocationRecord> findItem(
            List<ItemLocationRecord> items, ItemId itemId) {
        return items.stream().filter(item -> item.itemId().equals(itemId)).findFirst();
    }

    private static boolean isNative(EquipmentSlot slot) {
        return switch (slot) {
            case MAIN_HAND, OFF_HAND, HEAD, CHEST, LEGS, FEET -> true;
            case NECKLACE,
                    RING_ONE,
                    RING_TWO,
                    TALISMAN,
                    QUIVER,
                    COSMETIC_HEAD,
                    COSMETIC_CHEST,
                    COSMETIC_LEGS,
                    COSMETIC_FEET ->
                    false;
        };
    }

    private void releaseQuietly(
            CharacterId characterId, SessionId sessionId, CharacterLease lease) {
        database.leases()
                .release(characterId, database.serverInstanceId(), sessionId, lease.version());
    }

    private static <T, E extends com.branz.mmorpg.api.result.ErrorCode>
            Result<T, CharacterSessionErrorCode> persistenceFailure(E error, String detail) {
        return Result.failure(
                CharacterSessionErrorCode.CHARACTER_PERSISTENCE_UNAVAILABLE,
                error.code() + ": " + detail);
    }
}
