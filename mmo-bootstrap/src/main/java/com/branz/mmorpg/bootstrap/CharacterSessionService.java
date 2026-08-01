package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.identity.SessionId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.crossbow.CrossbowPersistentState;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.items.quiver.QuiverPreparation;
import com.branz.mmorpg.persistence.lease.CharacterLease;
import com.branz.mmorpg.persistence.lease.LeaseAcquireOutcome;
import com.branz.mmorpg.persistence.lease.LeaseErrorCode;
import com.branz.mmorpg.persistence.progression.KnowledgePersistenceErrorCode;
import com.branz.mmorpg.persistence.progression.KnowledgeRecord;
import com.branz.mmorpg.persistence.progression.ProgressionEvidenceExecution;
import com.branz.mmorpg.persistence.progression.ProgressionPersistenceErrorCode;
import com.branz.mmorpg.persistence.progression.ProgressionTrackRecord;
import com.branz.mmorpg.persistence.progression.RenownRecord;
import com.branz.mmorpg.persistence.progression.TeachingCommitExecution;
import com.branz.mmorpg.persistence.progression.TeachingCommitRequest;
import com.branz.mmorpg.persistence.transaction.CharacterBuildCommit;
import com.branz.mmorpg.persistence.transaction.CharacterBuildCommitExecution;
import com.branz.mmorpg.persistence.transaction.CharacterBuildRecord;
import com.branz.mmorpg.persistence.transaction.CrossbowBoltBinding;
import com.branz.mmorpg.persistence.transaction.ItemLocationMove;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.ItemPayloadUpdate;
import com.branz.mmorpg.persistence.transaction.JdbcValueTransactionService;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.LotQuantityConsumption;
import com.branz.mmorpg.persistence.transaction.LotQuantityTransfer;
import com.branz.mmorpg.persistence.transaction.NewItemLocation;
import com.branz.mmorpg.persistence.transaction.NewLotLocation;
import com.branz.mmorpg.persistence.transaction.ReconciliationErrorCode;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.persistence.transaction.TransactionExecution;
import com.branz.mmorpg.persistence.transaction.TransactionRequest;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import com.branz.mmorpg.progression.build.BuildEngine;
import com.branz.mmorpg.progression.build.BuildErrorCode;
import com.branz.mmorpg.progression.build.BuildResolution;
import com.branz.mmorpg.progression.build.CharacterBuild;
import com.branz.mmorpg.progression.build.CharacterBuildJsonCodec;
import com.branz.mmorpg.progression.evidence.EvidenceCandidate;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Blocking session/lease aggregate. Callers must run every method off the Paper thread. */
final class CharacterSessionService {
    private final DatabaseRuntime database;
    private final BuildEngine buildEngine;

    CharacterSessionService(DatabaseRuntime database) {
        this(database, BuildEngine.empty());
    }

    CharacterSessionService(DatabaseRuntime database, BuildEngine buildEngine) {
        this.database = Objects.requireNonNull(database, "database");
        this.buildEngine = Objects.requireNonNull(buildEngine, "buildEngine");
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
        Result<Optional<CharacterBuildRecord>, TransactionErrorCode> buildRow =
                database.builds().find(characterId);
        if (buildRow
                instanceof
                Result.Failure<Optional<CharacterBuildRecord>, TransactionErrorCode> failure) {
            releaseQuietly(characterId, sessionId, lease);
            return persistenceFailure(failure.error(), failure.detail());
        }
        Result<List<ProgressionTrackRecord>, ProgressionPersistenceErrorCode> progressionRows =
                database.progression().findTracks(characterId);
        if (progressionRows
                instanceof
                Result.Failure<List<ProgressionTrackRecord>, ProgressionPersistenceErrorCode>
                        failure) {
            releaseQuietly(characterId, sessionId, lease);
            return persistenceFailure(failure.error(), failure.detail());
        }
        Result<List<KnowledgeRecord>, KnowledgePersistenceErrorCode> knowledgeRows =
                database.knowledge().findKnowledge(characterId);
        if (knowledgeRows
                instanceof
                Result.Failure<List<KnowledgeRecord>, KnowledgePersistenceErrorCode> failure) {
            releaseQuietly(characterId, sessionId, lease);
            return persistenceFailure(failure.error(), failure.detail());
        }
        Result<Optional<RenownRecord>, KnowledgePersistenceErrorCode> renownRow =
                database.knowledge().findRenown(characterId);
        if (renownRow
                instanceof
                Result.Failure<Optional<RenownRecord>, KnowledgePersistenceErrorCode> failure) {
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
                                    .value(),
                            ((Result.Success<Optional<CharacterBuildRecord>, TransactionErrorCode>)
                                            buildRow)
                                    .value(),
                            ((Result.Success<
                                                    List<ProgressionTrackRecord>,
                                                    ProgressionPersistenceErrorCode>)
                                            progressionRows)
                                    .value(),
                            ((Result.Success<List<KnowledgeRecord>, KnowledgePersistenceErrorCode>)
                                            knowledgeRows)
                                    .value(),
                            ((Result.Success<Optional<RenownRecord>, KnowledgePersistenceErrorCode>)
                                            renownRow)
                                    .value());
            Result<BuildResolution, BuildErrorCode> buildResolution =
                    buildEngine.resolve(snapshot.build(), learnedKnowledge(snapshot));
            if (buildResolution
                    instanceof Result.Failure<BuildResolution, BuildErrorCode> failure) {
                releaseQuietly(characterId, sessionId, lease);
                return Result.failure(
                        CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                        failure.error().code() + ": " + failure.detail());
            }
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
        return grantTestValue(session, definition, inventorySlot, 1, contentVersion);
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> grantTestValue(
            LoadedCharacterSession session,
            ItemDefinition definition,
            int inventorySlot,
            int lotQuantity,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(contentVersion, "contentVersion");
        if (lotQuantity < 1 || lotQuantity > 4096) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Test lot quantity must be between 1 and 4096.");
        }
        if (definition.itemClass() == ItemClass.UNIQUE_DURABLE && lotQuantity != 1) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Unique test items must have quantity one.");
        }
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
                        "{\"definitionId\":\""
                                + definition.id().value()
                                + "\",\"quantity\":"
                                + lotQuantity
                                + "}",
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
                                            lotQuantity,
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

    Result<LoadedCharacterSession, CharacterSessionErrorCode> consumeAmmo(
            LoadedCharacterSession session,
            DefinitionId ammoDefinitionId,
            UUID projectileCommitId,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(ammoDefinitionId, "ammoDefinitionId");
        Objects.requireNonNull(projectileCommitId, "projectileCommitId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        ItemId quiverId = session.snapshot().equipment().item(EquipmentSlot.QUIVER).orElse(null);
        LotLocationRecord ammo =
                quiverId == null
                        ? null
                        : QuiverAmmoLots.select(
                                        session.snapshot().lotRecords(), quiverId, ammoDefinitionId)
                                .orElse(null);
        if (ammo == null) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_AMMO_UNAVAILABLE,
                    "No lot is stored in the equipped Quiver for " + ammoDefinitionId.value());
        }
        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(projectileCommitId),
                        "ammo-release:" + projectileCommitId,
                        session.characterId(),
                        session.sessionId(),
                        JdbcValueTransactionService.LOT_CONSUME,
                        "{\"lotId\":\""
                                + ammo.lotId().value()
                                + "\",\"version\":"
                                + ammo.version()
                                + ",\"quantity\":1}",
                        "{\"projectileId\":\""
                                + projectileCommitId
                                + "\",\"ammoDefinitionId\":\""
                                + ammoDefinitionId.value()
                                + "\"}",
                        contentVersion);
        Result<TransactionExecution, TransactionErrorCode> consumed =
                database.values()
                        .consumeLot(
                                request,
                                new LotQuantityConsumption(
                                        ammo.lotId(),
                                        ammo.version(),
                                        ammo.ownerCharacterId(),
                                        ammo.location(),
                                        1));
        if (consumed
                instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    failure.error().code() + ": " + failure.detail());
        }
        return reload(session);
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> bindCrossbowBolt(
            LoadedCharacterSession session,
            ItemId crossbowItemId,
            DefinitionId boltDefinitionId,
            UUID operationId,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(crossbowItemId, "crossbowItemId");
        Objects.requireNonNull(boltDefinitionId, "boltDefinitionId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        ItemLocationRecord crossbow = equippedMainHand(session, crossbowItemId);
        if (crossbow == null) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Crossbow bolt binding requires the same authoritative main-hand item.");
        }
        CrossbowPersistentState current;
        try {
            current = CrossbowPayloadCodec.decode(crossbow.payloadJson());
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID, exception.getMessage());
        }
        if (!current.equals(CrossbowPersistentState.unloaded())) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Crossbow is not at the UNLOADED checkpoint.");
        }
        ItemId quiverId = session.snapshot().equipment().item(EquipmentSlot.QUIVER).orElse(null);
        LotLocationRecord bolt =
                quiverId == null
                        ? null
                        : QuiverAmmoLots.select(
                                        session.snapshot().lotRecords(), quiverId, boltDefinitionId)
                                .orElse(null);
        if (bolt == null) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_AMMO_UNAVAILABLE,
                    "No bolt lot is stored in the equipped Quiver for " + boltDefinitionId.value());
        }
        String replacement;
        try {
            replacement =
                    CrossbowPayloadCodec.encode(
                            crossbow.payloadJson(),
                            CrossbowPersistentState.boltPlaced(boltDefinitionId));
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID, exception.getMessage());
        }
        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(operationId),
                        "crossbow-bolt-bind:" + operationId,
                        session.characterId(),
                        session.sessionId(),
                        JdbcValueTransactionService.CROSSBOW_BOLT_BIND,
                        "{\"itemId\":\""
                                + crossbow.itemId().value()
                                + "\",\"itemVersion\":"
                                + crossbow.version()
                                + ",\"lotId\":\""
                                + bolt.lotId().value()
                                + "\",\"lotVersion\":"
                                + bolt.version()
                                + "}",
                        "{\"checkpoint\":\"BOLT_PLACED\",\"ammoDefinitionId\":\""
                                + boltDefinitionId.value()
                                + "\"}",
                        contentVersion);
        Result<TransactionExecution, TransactionErrorCode> bound =
                database.values()
                        .bindCrossbowBolt(
                                request,
                                new CrossbowBoltBinding(
                                        new ItemPayloadUpdate(
                                                crossbow.itemId(),
                                                crossbow.version(),
                                                crossbow.ownerCharacterId(),
                                                crossbow.location(),
                                                crossbow.payloadJson(),
                                                replacement),
                                        new LotQuantityConsumption(
                                                bolt.lotId(),
                                                bolt.version(),
                                                bolt.ownerCharacterId(),
                                                bolt.location(),
                                                1),
                                        boltDefinitionId));
        if (bound instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
            return transactionFailure(failure);
        }
        return reload(session);
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> completeCrossbowLoad(
            LoadedCharacterSession session,
            ItemId crossbowItemId,
            DefinitionId boundBoltDefinitionId,
            UUID operationId,
            String contentVersion) {
        return updateCrossbowCheckpoint(
                session,
                crossbowItemId,
                CrossbowPersistentState.boltPlaced(boundBoltDefinitionId),
                CrossbowPersistentState.loaded(boundBoltDefinitionId),
                operationId,
                "crossbow-loaded:",
                contentVersion);
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> fireCrossbow(
            LoadedCharacterSession session,
            ItemId crossbowItemId,
            DefinitionId boundBoltDefinitionId,
            UUID projectileId,
            String contentVersion) {
        return updateCrossbowCheckpoint(
                session,
                crossbowItemId,
                CrossbowPersistentState.loaded(boundBoltDefinitionId),
                CrossbowPersistentState.unloaded(),
                projectileId,
                "crossbow-fire:",
                contentVersion);
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> commitCatalystUse(
            LoadedCharacterSession session,
            ItemId catalystItemId,
            DefinitionId expectedDefinitionId,
            int baseMaximumDurability,
            int durabilityCost,
            DefinitionId spellId,
            UUID operationId,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(catalystItemId, "catalystItemId");
        Objects.requireNonNull(expectedDefinitionId, "expectedDefinitionId");
        Objects.requireNonNull(spellId, "spellId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        ItemLocationRecord catalyst = equippedMainHand(session, catalystItemId);
        if (catalyst == null || !catalyst.definitionId().equals(expectedDefinitionId)) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Spell commit requires the same authoritative main-hand catalyst.");
        }
        String replacement;
        CatalystDurability next;
        try {
            CatalystDurability current =
                    CatalystPayloadCodec.decode(catalyst.payloadJson(), baseMaximumDurability);
            next = current.spend(durabilityCost);
            replacement = CatalystPayloadCodec.encode(catalyst.payloadJson(), next);
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID, exception.getMessage());
        }
        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(operationId),
                        "spell-catalyst:" + operationId,
                        session.characterId(),
                        session.sessionId(),
                        JdbcValueTransactionService.ITEM_PAYLOAD_UPDATE,
                        "{\"itemId\":\""
                                + catalyst.itemId().value()
                                + "\",\"itemVersion\":"
                                + catalyst.version()
                                + ",\"spellId\":\""
                                + spellId.value()
                                + "\"}",
                        "{\"currentDurability\":" + next.current() + "}",
                        contentVersion);
        Result<TransactionExecution, TransactionErrorCode> updated =
                database.values()
                        .updateItemPayload(
                                request,
                                new ItemPayloadUpdate(
                                        catalyst.itemId(),
                                        catalyst.version(),
                                        catalyst.ownerCharacterId(),
                                        catalyst.location(),
                                        catalyst.payloadJson(),
                                        replacement));
        if (updated instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
            return transactionFailure(failure);
        }
        return reload(session);
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> transferQuiverAmmo(
            LoadedCharacterSession session,
            LotId sourceLotId,
            long quantity,
            boolean store,
            int quiverCapacity,
            UUID operationId,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(sourceLotId, "sourceLotId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        if (quantity < 1) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Quiver transfer quantity must be positive.");
        }
        ItemId quiverId = session.snapshot().equipment().item(EquipmentSlot.QUIVER).orElse(null);
        ItemLocationRecord quiver =
                quiverId == null
                        ? null
                        : findItem(session.snapshot().itemRecords(), quiverId).orElse(null);
        LotLocationRecord source =
                session.snapshot().lotRecords().stream()
                        .filter(record -> record.lotId().equals(sourceLotId))
                        .findFirst()
                        .orElse(null);
        ValueLocation equippedLocation = ValueLocation.virtualEquipped(EquipmentSlot.QUIVER.name());
        ValueLocation storedLocation = quiverId == null ? null : ValueLocation.quiver(quiverId);
        if (quiver == null
                || source == null
                || !quiver.location().equals(equippedLocation)
                || quantity > source.quantity()
                || (store
                        && source.location().type()
                                != com.branz.mmorpg.persistence.transaction.ValueLocationType
                                        .CHARACTER_INVENTORY)
                || (!store && !source.location().equals(storedLocation))) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Quiver transfer source or equipped container changed.");
        }
        ValueLocation destination;
        if (store) {
            destination = storedLocation;
        } else {
            int freeSlot = firstFreeInventorySlot(session.snapshot());
            if (freeSlot < 0) {
                return Result.failure(
                        CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                        "No free MMO inventory slot is available.");
            }
            destination = ValueLocation.inventory("slot:" + freeSlot);
        }
        LotId destinationLotId =
                quantity == source.quantity() ? source.lotId() : new LotId(operationId);
        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(operationId),
                        "quiver-lot-transfer:" + operationId,
                        session.characterId(),
                        session.sessionId(),
                        JdbcValueTransactionService.LOT_TRANSFER,
                        "{\"sourceLotId\":\""
                                + source.lotId().value()
                                + "\",\"version\":"
                                + source.version()
                                + ",\"quantity\":"
                                + quantity
                                + "}",
                        "{\"destinationLotId\":\""
                                + destinationLotId.value()
                                + "\",\"location\":\""
                                + destination.type().name()
                                + "\"}",
                        contentVersion);
        Result<TransactionExecution, TransactionErrorCode> transferred =
                database.values()
                        .transferLotQuantity(
                                request,
                                new LotQuantityTransfer(
                                        source.lotId(),
                                        destinationLotId,
                                        source.version(),
                                        source.quantity(),
                                        source.ownerCharacterId(),
                                        source.location(),
                                        destination,
                                        quantity,
                                        quiver.itemId(),
                                        quiver.version(),
                                        quiver.ownerCharacterId(),
                                        quiver.location(),
                                        quiverCapacity));
        if (transferred
                instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    failure.error().code() + ": " + failure.detail());
        }
        return reload(session);
    }

    Result<LoadedCharacterSession, CharacterSessionErrorCode> updateQuiverPreparation(
            LoadedCharacterSession session,
            QuiverPreparation desired,
            UUID operationId,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(desired, "desired");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        if (desired.equals(session.snapshot().quiverPreparation())) {
            return Result.success(session);
        }
        ItemId quiverId = session.snapshot().equipment().item(EquipmentSlot.QUIVER).orElse(null);
        ItemLocationRecord quiver =
                quiverId == null
                        ? null
                        : findItem(session.snapshot().itemRecords(), quiverId).orElse(null);
        ValueLocation expectedLocation = ValueLocation.virtualEquipped(EquipmentSlot.QUIVER.name());
        if (quiver == null || !quiver.location().equals(expectedLocation)) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Prepared ammo requires one authoritative equipped Quiver item.");
        }
        String replacement;
        try {
            replacement = QuiverPayloadCodec.encode(quiver.payloadJson(), desired);
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID, exception.getMessage());
        }
        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(operationId),
                        "quiver-preparation:" + operationId,
                        session.characterId(),
                        session.sessionId(),
                        JdbcValueTransactionService.ITEM_PAYLOAD_UPDATE,
                        "{\"itemId\":\""
                                + quiver.itemId().value()
                                + "\",\"version\":"
                                + quiver.version()
                                + "}",
                        "{\"preparedAmmo\":"
                                + desired.preparedAmmo().size()
                                + ",\"selectedIndex\":"
                                + desired.selectedIndex()
                                + "}",
                        contentVersion);
        Result<TransactionExecution, TransactionErrorCode> updated =
                database.values()
                        .updateItemPayload(
                                request,
                                new ItemPayloadUpdate(
                                        quiver.itemId(),
                                        quiver.version(),
                                        quiver.ownerCharacterId(),
                                        quiver.location(),
                                        quiver.payloadJson(),
                                        replacement));
        if (updated instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
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
        if (changed.size() > 2) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    "One equipment transaction may change at most two linked slots.");
        }
        List<ItemLocationMove> moves = new java.util.ArrayList<>();
        java.util.EnumMap<EquipmentSlot, ItemLocationRecord> incomingBySlot =
                new java.util.EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : changed) {
            Optional<ItemId> desiredItem = desired.item(slot);
            if (desiredItem.isEmpty()) {
                continue;
            }
            ItemLocationRecord incoming =
                    findItem(session.snapshot().itemRecords(), desiredItem.orElseThrow())
                            .orElse(null);
            if (incoming == null
                    || incoming.location().type()
                            != com.branz.mmorpg.persistence.transaction.ValueLocationType
                                    .CHARACTER_INVENTORY) {
                return Result.failure(
                        CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                        "Selected item is not in this character's inventory.");
            }
            incomingBySlot.put(slot, incoming);
            moves.add(
                    new ItemLocationMove(
                            incoming.itemId(),
                            incoming.version(),
                            incoming.ownerCharacterId(),
                            incoming.location(),
                            incoming.ownerCharacterId(),
                            equippedLocation(slot)));
        }
        java.util.Set<Integer> claimedInventorySlots = new java.util.HashSet<>();
        for (EquipmentSlot slot : changed) {
            Optional<ItemId> currentItem = session.snapshot().equipment().item(slot);
            if (currentItem.isEmpty()) {
                continue;
            }
            ValueLocation equippedLocation = equippedLocation(slot);
            ItemLocationRecord displaced =
                    findItem(session.snapshot().itemRecords(), currentItem.orElseThrow())
                            .orElse(null);
            if (displaced == null || !displaced.location().equals(equippedLocation)) {
                return Result.failure(
                        CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                        "Committed equipment row does not match the Scene loadout.");
            }
            ItemLocationRecord incoming = incomingBySlot.get(slot);
            ValueLocation destination;
            if (incoming != null) {
                destination = incoming.location();
                claimedInventorySlots.add(inventorySlot(destination));
            } else {
                int freeSlot = firstFreeInventorySlot(session.snapshot(), claimedInventorySlots);
                if (freeSlot < 0) {
                    return Result.failure(
                            CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                            "Unequip requires one free MMO inventory slot.");
                }
                claimedInventorySlots.add(freeSlot);
                destination = ValueLocation.inventory("slot:" + freeSlot);
            }
            moves.add(
                    new ItemLocationMove(
                            displaced.itemId(),
                            displaced.version(),
                            displaced.ownerCharacterId(),
                            displaced.location(),
                            displaced.ownerCharacterId(),
                            destination));
        }
        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(UUID.randomUUID()),
                        "scene-equip:" + session.sessionId().value() + ":" + UUID.randomUUID(),
                        session.characterId(),
                        session.sessionId(),
                        JdbcValueTransactionService.ITEM_BATCH_MOVE,
                        "{\"slots\":\""
                                + changed.stream()
                                        .map(EquipmentSlot::name)
                                        .collect(java.util.stream.Collectors.joining(","))
                                + "\"}",
                        "{\"changedSlots\":" + changed.size() + "}",
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

    Result<LoadedCharacterSession, CharacterSessionErrorCode> commitBuild(
            LoadedCharacterSession session,
            CharacterBuild desired,
            UUID operationId,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(desired, "desired");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Result<BuildResolution, BuildErrorCode> validation =
                buildEngine.resolve(desired, learnedKnowledge(session.snapshot()));
        if (validation instanceof Result.Failure<BuildResolution, BuildErrorCode> failure) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    failure.error().code() + ": " + failure.detail());
        }
        if (desired.equals(session.snapshot().build())) {
            return Result.success(session);
        }
        long expectedVersion =
                session.snapshot().buildRecord().map(CharacterBuildRecord::version).orElse(0L);
        String payload = CharacterBuildJsonCodec.encode(desired);
        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(operationId),
                        "character-build:" + operationId,
                        session.characterId(),
                        session.sessionId(),
                        com.branz.mmorpg.persistence.transaction.JdbcCharacterBuildRepository
                                .CHARACTER_BUILD_COMMIT,
                        "{\"expectedVersion\":" + expectedVersion + "}",
                        "{\"attunementCapacity\":"
                                + desired.attunementCapacity()
                                + ",\"techniqueCount\":"
                                + desired.techniques().size()
                                + "}",
                        contentVersion);
        Result<CharacterBuildCommitExecution, TransactionErrorCode> committed =
                database.builds()
                        .commit(
                                request,
                                new CharacterBuildCommit(
                                        session.characterId(), expectedVersion, payload));
        if (committed
                instanceof
                Result.Failure<CharacterBuildCommitExecution, TransactionErrorCode> failure) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                    failure.error().code() + ": " + failure.detail());
        }
        return reload(session);
    }

    Result<ProgressionEvidenceCommitResult, CharacterSessionErrorCode> recordProgressionEvidence(
            LoadedCharacterSession session, List<EvidenceCandidate> candidates) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.stream()
                .anyMatch(candidate -> !candidate.characterId().equals(session.characterId()))) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Progression evidence character does not own this session.");
        }
        Result<List<ProgressionEvidenceExecution>, ProgressionPersistenceErrorCode> recorded =
                database.progression().recordBatch(candidates);
        if (recorded
                instanceof
                Result.Failure<List<ProgressionEvidenceExecution>, ProgressionPersistenceErrorCode>
                        failure) {
            CharacterSessionErrorCode error =
                    failure.error()
                                    == ProgressionPersistenceErrorCode
                                            .PROGRESSION_DATABASE_UNAVAILABLE
                            ? CharacterSessionErrorCode.CHARACTER_PERSISTENCE_UNAVAILABLE
                            : CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED;
            return Result.failure(error, failure.error().code() + ": " + failure.detail());
        }
        Result<LoadedCharacterSession, CharacterSessionErrorCode> reloaded = reload(session);
        if (reloaded
                instanceof
                Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        return Result.success(
                new ProgressionEvidenceCommitResult(
                        ((Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>)
                                        reloaded)
                                .value(),
                        ((Result.Success<
                                                List<ProgressionEvidenceExecution>,
                                                ProgressionPersistenceErrorCode>)
                                        recorded)
                                .value()));
    }

    Result<TeachingSessionCommitResult, CharacterSessionErrorCode> commitTeaching(
            LoadedCharacterSession teacherSession,
            LoadedCharacterSession studentSession,
            TeachingCommitRequest request) {
        Objects.requireNonNull(teacherSession, "teacherSession");
        Objects.requireNonNull(studentSession, "studentSession");
        Objects.requireNonNull(request, "request");
        if (!teacherSession.characterId().equals(request.completion().teacherId())
                || !studentSession.characterId().equals(request.completion().studentId())) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Teaching completion participants do not own these Player Sessions.");
        }
        Result<TeachingCommitExecution, KnowledgePersistenceErrorCode> committed =
                database.knowledge().commitTeaching(request);
        if (committed
                instanceof
                Result.Failure<TeachingCommitExecution, KnowledgePersistenceErrorCode> failure) {
            CharacterSessionErrorCode error =
                    failure.error() == KnowledgePersistenceErrorCode.KNOWLEDGE_DATABASE_UNAVAILABLE
                            ? CharacterSessionErrorCode.CHARACTER_PERSISTENCE_UNAVAILABLE
                            : CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED;
            return Result.failure(error, failure.error().code() + ": " + failure.detail());
        }
        Result<LoadedCharacterSession, CharacterSessionErrorCode> reloadedTeacher =
                reload(teacherSession);
        if (reloadedTeacher
                instanceof
                Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        Result<LoadedCharacterSession, CharacterSessionErrorCode> reloadedStudent =
                reload(studentSession);
        if (reloadedStudent
                instanceof
                Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        return Result.success(
                new TeachingSessionCommitResult(
                        ((Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>)
                                        reloadedTeacher)
                                .value(),
                        ((Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>)
                                        reloadedStudent)
                                .value(),
                        ((Result.Success<TeachingCommitExecution, KnowledgePersistenceErrorCode>)
                                        committed)
                                .value()));
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
        Result<Optional<CharacterBuildRecord>, TransactionErrorCode> buildRow =
                database.builds().find(session.characterId());
        if (buildRow
                instanceof
                Result.Failure<Optional<CharacterBuildRecord>, TransactionErrorCode> failure) {
            return persistenceFailure(failure.error(), failure.detail());
        }
        Result<List<ProgressionTrackRecord>, ProgressionPersistenceErrorCode> progressionRows =
                database.progression().findTracks(session.characterId());
        if (progressionRows
                instanceof
                Result.Failure<List<ProgressionTrackRecord>, ProgressionPersistenceErrorCode>
                        failure) {
            return persistenceFailure(failure.error(), failure.detail());
        }
        Result<List<KnowledgeRecord>, KnowledgePersistenceErrorCode> knowledgeRows =
                database.knowledge().findKnowledge(session.characterId());
        if (knowledgeRows
                instanceof
                Result.Failure<List<KnowledgeRecord>, KnowledgePersistenceErrorCode> failure) {
            return persistenceFailure(failure.error(), failure.detail());
        }
        Result<Optional<RenownRecord>, KnowledgePersistenceErrorCode> renownRow =
                database.knowledge().findRenown(session.characterId());
        if (renownRow
                instanceof
                Result.Failure<Optional<RenownRecord>, KnowledgePersistenceErrorCode> failure) {
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
                                    .value(),
                            ((Result.Success<Optional<CharacterBuildRecord>, TransactionErrorCode>)
                                            buildRow)
                                    .value(),
                            ((Result.Success<
                                                    List<ProgressionTrackRecord>,
                                                    ProgressionPersistenceErrorCode>)
                                            progressionRows)
                                    .value(),
                            ((Result.Success<List<KnowledgeRecord>, KnowledgePersistenceErrorCode>)
                                            knowledgeRows)
                                    .value(),
                            ((Result.Success<Optional<RenownRecord>, KnowledgePersistenceErrorCode>)
                                            renownRow)
                                    .value());
            return Result.success(
                    new LoadedCharacterSession(
                            session.characterId(), session.sessionId(), session.lease(), snapshot));
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID, exception.getMessage());
        }
    }

    private Result<LoadedCharacterSession, CharacterSessionErrorCode> updateCrossbowCheckpoint(
            LoadedCharacterSession session,
            ItemId crossbowItemId,
            CrossbowPersistentState expected,
            CrossbowPersistentState replacementState,
            UUID operationId,
            String idempotencyPrefix,
            String contentVersion) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(crossbowItemId, "crossbowItemId");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(replacementState, "replacementState");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(idempotencyPrefix, "idempotencyPrefix");
        Objects.requireNonNull(contentVersion, "contentVersion");
        ItemLocationRecord crossbow = equippedMainHand(session, crossbowItemId);
        if (crossbow == null) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                    "Crossbow checkpoint update requires the same authoritative main-hand item.");
        }
        String replacement;
        try {
            CrossbowPersistentState current = CrossbowPayloadCodec.decode(crossbow.payloadJson());
            if (!current.equals(expected)) {
                return Result.failure(
                        CharacterSessionErrorCode.CHARACTER_STATE_INVALID,
                        "Crossbow durable checkpoint changed; expected "
                                + expected.checkpoint().name()
                                + " but found "
                                + current.checkpoint().name()
                                + ".");
            }
            replacement = CrossbowPayloadCodec.encode(crossbow.payloadJson(), replacementState);
        } catch (IllegalArgumentException exception) {
            return Result.failure(
                    CharacterSessionErrorCode.CHARACTER_STATE_INVALID, exception.getMessage());
        }
        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(operationId),
                        idempotencyPrefix + operationId,
                        session.characterId(),
                        session.sessionId(),
                        JdbcValueTransactionService.ITEM_PAYLOAD_UPDATE,
                        "{\"itemId\":\""
                                + crossbow.itemId().value()
                                + "\",\"version\":"
                                + crossbow.version()
                                + ",\"checkpoint\":\""
                                + expected.checkpoint().name()
                                + "\"}",
                        "{\"checkpoint\":\"" + replacementState.checkpoint().name() + "\"}",
                        contentVersion);
        Result<TransactionExecution, TransactionErrorCode> updated =
                database.values()
                        .updateItemPayload(
                                request,
                                new ItemPayloadUpdate(
                                        crossbow.itemId(),
                                        crossbow.version(),
                                        crossbow.ownerCharacterId(),
                                        crossbow.location(),
                                        crossbow.payloadJson(),
                                        replacement));
        if (updated instanceof Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
            return transactionFailure(failure);
        }
        return reload(session);
    }

    private static ItemLocationRecord equippedMainHand(
            LoadedCharacterSession session, ItemId expectedItemId) {
        ItemId equipped = session.snapshot().equipment().item(EquipmentSlot.MAIN_HAND).orElse(null);
        ItemLocationRecord item =
                equipped == null
                        ? null
                        : findItem(session.snapshot().itemRecords(), equipped).orElse(null);
        return item != null
                        && item.itemId().equals(expectedItemId)
                        && item.location().equals(ValueLocation.nativeEquipped("MAIN_HAND"))
                ? item
                : null;
    }

    private static Set<KnowledgeKey> learnedKnowledge(PersistentCharacterSnapshot snapshot) {
        return snapshot.learnedKnowledge().stream()
                .map(KnowledgeRecord::knowledge)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Result<LoadedCharacterSession, CharacterSessionErrorCode> transactionFailure(
            Result.Failure<TransactionExecution, TransactionErrorCode> failure) {
        return Result.failure(
                CharacterSessionErrorCode.CHARACTER_TRANSACTION_REJECTED,
                failure.error().code() + ": " + failure.detail());
    }

    private static Optional<ItemLocationRecord> findItem(
            List<ItemLocationRecord> items, ItemId itemId) {
        return items.stream().filter(item -> item.itemId().equals(itemId)).findFirst();
    }

    private static int firstFreeInventorySlot(PersistentCharacterSnapshot snapshot) {
        return firstFreeInventorySlot(snapshot, java.util.Set.of());
    }

    private static int firstFreeInventorySlot(
            PersistentCharacterSnapshot snapshot, java.util.Set<Integer> additionallyClaimed) {
        boolean[] occupied = new boolean[36];
        occupied[ChronicleService.HOTBAR_SLOT] = true;
        snapshot.inventory().forEach(projection -> occupied[projection.slot()] = true);
        for (int slot = 0; slot < occupied.length; slot++) {
            if (!occupied[slot] && !additionallyClaimed.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private static int inventorySlot(ValueLocation location) {
        String reference = location.reference().orElseThrow();
        if (location.type()
                        != com.branz.mmorpg.persistence.transaction.ValueLocationType
                                .CHARACTER_INVENTORY
                || !reference.startsWith("slot:")) {
            throw new IllegalArgumentException("Expected an inventory slot location.");
        }
        return Integer.parseInt(reference.substring("slot:".length()));
    }

    private static ValueLocation equippedLocation(EquipmentSlot slot) {
        return isNative(slot)
                ? ValueLocation.nativeEquipped(slot.name())
                : ValueLocation.virtualEquipped(slot.name());
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
