package com.branz.mmorpg.persistence.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.identity.SessionId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.migration.ClasspathMigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationErrorCode;
import com.branz.mmorpg.persistence.migration.PostgresMigrationRunner;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcValueTransactionServiceIntegrationTest {
    private static final CharacterId CHARACTER = new CharacterId(UUID.randomUUID());
    private static final SessionId SESSION = new SessionId(UUID.randomUUID());
    private static final DefinitionId ITEM_DEFINITION = DefinitionId.of("item.test.relic");
    private static final DefinitionId LOT_DEFINITION = DefinitionId.of("material.test.ore");

    private EmbeddedPostgres postgres;
    private DataSource dataSource;
    private JdbcTransactionJournalRepository journal;
    private JdbcAuditLogRepository audit;
    private JdbcReconciliationScanner reconciliation;
    private JdbcValueTransactionService service;

    @BeforeAll
    void startPostgresAndMigrate() throws Exception {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
        dataSource = postgres.getPostgresDatabase();
        Result<MigrationCatalog, MigrationErrorCode> loaded =
                ClasspathMigrationCatalog.loadDefault();
        assertTrue(loaded.isSuccess());
        MigrationCatalog catalog =
                ((Result.Success<MigrationCatalog, MigrationErrorCode>) loaded).value();
        assertTrue(new PostgresMigrationRunner(dataSource).migrate(catalog).isSuccess());
        journal = new JdbcTransactionJournalRepository(dataSource);
        audit = new JdbcAuditLogRepository(dataSource);
        reconciliation = new JdbcReconciliationScanner(dataSource);
        service = new JdbcValueTransactionService(dataSource);
    }

    @AfterAll
    void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void clearValueState() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "TRUNCATE TABLE item_instance, commodity_lot, transaction_journal CASCADE");
        }
    }

    @Test
    void journalCanonicalizesJsonForReplayAndKeepsTerminalStateImmutable() {
        TransactionRequest first =
                request(
                        "reward:test:one",
                        "reward.prepare",
                        "{\"slot\":9,\"source\":\"boss\"}",
                        "{\"coins\":10,\"item\":\"item.test.relic\"}");

        JournalPrepareOutcome prepared = success(journal.prepare(first));
        assertTrue(prepared.newlyPrepared());
        assertEquals(TransactionState.PREPARED, prepared.entry().state());

        TransactionRequest retry =
                new TransactionRequest(
                        transactionId(),
                        first.idempotencyKey(),
                        first.characterId(),
                        first.sessionId(),
                        first.operationType(),
                        "{ \"source\": \"boss\", \"slot\": 9 }",
                        "{\"item\":\"item.test.relic\",\"coins\":10}",
                        first.contentVersion());
        JournalPrepareOutcome replay = success(journal.prepare(retry));
        assertFalse(replay.newlyPrepared());
        assertEquals(first.transactionId(), replay.entry().transactionId());

        JournalTransitionOutcome committed =
                success(journal.transition(first.transactionId(), TransactionState.COMMITTED));
        assertTrue(committed.changed());
        assertEquals(TransactionState.COMMITTED, committed.entry().state());
        assertFalse(
                success(journal.transition(first.transactionId(), TransactionState.COMMITTED))
                        .changed());
        assertEquals(
                TransactionErrorCode.TRANSACTION_INVALID_STATE,
                failure(journal.transition(first.transactionId(), TransactionState.ROLLED_BACK)));

        TransactionRequest conflicting =
                new TransactionRequest(
                        transactionId(),
                        first.idempotencyKey(),
                        first.characterId(),
                        first.sessionId(),
                        first.operationType(),
                        first.reservedInputsJson(),
                        "{\"coins\":11}",
                        first.contentVersion());
        assertEquals(
                TransactionErrorCode.TRANSACTION_IDEMPOTENCY_CONFLICT,
                failure(journal.prepare(conflicting)));
    }

    @Test
    void invalidJsonFailsWithoutLeavingAPreparedJournal() {
        TransactionRequest invalid =
                request("reward:test:invalid", "reward.prepare", "{not-json", "{}");

        assertEquals(
                TransactionErrorCode.TRANSACTION_INVALID_JSON, failure(journal.prepare(invalid)));
        assertEquals(Optional.empty(), success(journal.find(invalid.transactionId())));
    }

    @Test
    void crashAfterItemMutationRollsBackThenRetryGrantsExactlyOnce() {
        ItemId itemId = new ItemId(UUID.randomUUID());
        NewItemLocation item =
                new NewItemLocation(
                        itemId,
                        ITEM_DEFINITION,
                        Optional.of(CHARACTER),
                        ValueLocation.pendingRewards("reward-1"),
                        "{\"roll\":7}");
        TransactionRequest request =
                request(
                        "item:grant:" + itemId.value(),
                        JdbcValueTransactionService.ITEM_GRANT,
                        "{}",
                        "{\"itemId\":\"" + itemId.value() + "\"}");
        JdbcValueTransactionService crashing =
                new JdbcValueTransactionService(
                        dataSource,
                        checkpoint -> {
                            if (checkpoint
                                    == JdbcValueTransactionService.Checkpoint.AFTER_MUTATION) {
                                throw new SimulatedCrash();
                            }
                        });

        assertThrows(SimulatedCrash.class, () -> crashing.grantItem(request, item));
        assertEquals(Optional.empty(), success(service.findItem(itemId)));
        assertEquals(Optional.empty(), success(journal.find(request.transactionId())));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM audit_log"));

        TransactionExecution committed = success(service.grantItem(request, item));
        assertFalse(committed.replayed());
        assertEquals(TransactionState.COMMITTED, committed.journalEntry().state());

        TransactionRequest retry = retryRequest(request);
        TransactionExecution replay = success(service.grantItem(retry, item));
        assertTrue(replay.replayed());
        assertEquals(request.transactionId(), replay.journalEntry().transactionId());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM item_instance"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM transaction_journal"));
        List<AuditLogEntry> auditEntries =
                success(audit.findByTransaction(request.transactionId()));
        assertEquals(1, auditEntries.size());
        assertEquals(AuditSubjectType.ITEM, auditEntries.getFirst().subjectType());
        assertEquals(itemId.value(), auditEntries.getFirst().subjectId());
    }

    @Test
    void itemMoveUsesVersionOwnerAndLocationCompareAndSet() {
        ItemId itemId = new ItemId(UUID.randomUUID());
        ValueLocation pending = ValueLocation.pendingRewards("reward-cas");
        grantItem(itemId, pending);

        ItemLocationMove move =
                new ItemLocationMove(
                        itemId,
                        1,
                        Optional.of(CHARACTER),
                        pending,
                        Optional.of(CHARACTER),
                        ValueLocation.inventory("slot:4"));
        TransactionRequest moveRequest =
                request(
                        "item:move:" + itemId.value(),
                        JdbcValueTransactionService.ITEM_MOVE,
                        "{\"version\":1}",
                        "{\"location\":\"CHARACTER_INVENTORY\"}");

        assertFalse(success(service.moveItem(moveRequest, move)).replayed());
        assertTrue(success(service.moveItem(retryRequest(moveRequest), move)).replayed());

        ItemLocationRecord current = success(service.findItem(itemId)).orElseThrow();
        assertEquals(2, current.version());
        assertEquals(ValueLocation.inventory("slot:4"), current.location());

        TransactionRequest staleRequest =
                request(
                        "item:move:stale:" + itemId.value(),
                        JdbcValueTransactionService.ITEM_MOVE,
                        "{}",
                        "{}");
        assertEquals(
                TransactionErrorCode.VALUE_STALE_VERSION,
                failure(service.moveItem(staleRequest, move)));
        assertEquals(Optional.empty(), success(journal.find(staleRequest.transactionId())));

        ItemLocationMove wrongSource =
                new ItemLocationMove(
                        itemId,
                        2,
                        Optional.of(CHARACTER),
                        ValueLocation.pendingRewards("wrong"),
                        Optional.of(CHARACTER),
                        ValueLocation.quarantine("case-1"));
        assertEquals(
                TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                failure(
                        service.moveItem(
                                request(
                                        "item:move:wrong:" + itemId.value(),
                                        JdbcValueTransactionService.ITEM_MOVE,
                                        "{}",
                                        "{}"),
                                wrongSource)));
    }

    @Test
    void concurrentItemMovesCommitOnlyOneDestination() {
        ItemId itemId = new ItemId(UUID.randomUUID());
        ValueLocation source = ValueLocation.pendingRewards("reward-race");
        grantItem(itemId, source);

        ItemLocationMove overflow =
                new ItemLocationMove(
                        itemId,
                        1,
                        Optional.of(CHARACTER),
                        source,
                        Optional.of(CHARACTER),
                        ValueLocation.overflowClaim("overflow-1"));
        ItemLocationMove quarantine =
                new ItemLocationMove(
                        itemId,
                        1,
                        Optional.of(CHARACTER),
                        source,
                        Optional.of(CHARACTER),
                        ValueLocation.quarantine("case-race"));

        CompletableFuture<Result<TransactionExecution, TransactionErrorCode>> first =
                CompletableFuture.supplyAsync(
                        () ->
                                service.moveItem(
                                        request(
                                                "item:race:first:" + itemId.value(),
                                                JdbcValueTransactionService.ITEM_MOVE,
                                                "{}",
                                                "{\"destination\":\"overflow\"}"),
                                        overflow));
        CompletableFuture<Result<TransactionExecution, TransactionErrorCode>> second =
                CompletableFuture.supplyAsync(
                        () ->
                                service.moveItem(
                                        request(
                                                "item:race:second:" + itemId.value(),
                                                JdbcValueTransactionService.ITEM_MOVE,
                                                "{}",
                                                "{\"destination\":\"quarantine\"}"),
                                        quarantine));
        List<Result<TransactionExecution, TransactionErrorCode>> results =
                List.of(first.join(), second.join());

        assertEquals(1, results.stream().filter(Result::isSuccess).count());
        assertEquals(
                1,
                results.stream()
                        .filter(result -> !result.isSuccess())
                        .map(JdbcValueTransactionServiceIntegrationTest::failure)
                        .filter(TransactionErrorCode.VALUE_STALE_VERSION::equals)
                        .count());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM item_instance"));
        assertEquals(2, success(service.findItem(itemId)).orElseThrow().version());
    }

    @Test
    void itemPayloadUpdateRollsBackOnCrashAndReplaysExactlyOnce() {
        ItemId itemId = new ItemId(UUID.randomUUID());
        ValueLocation quiver = ValueLocation.virtualEquipped("QUIVER");
        grantItem(itemId, quiver);
        String replacement =
                "{\"displayRevision\":2,\"quiver\":{\"preparedAmmo\":[\"ammo.test.arrow\"],\"selectedIndex\":0}}";
        ItemPayloadUpdate update =
                new ItemPayloadUpdate(itemId, 1, Optional.of(CHARACTER), quiver, "{}", replacement);
        TransactionRequest request =
                request(
                        "item:payload:" + itemId.value(),
                        JdbcValueTransactionService.ITEM_PAYLOAD_UPDATE,
                        "{\"version\":1}",
                        "{\"preparedAmmo\":1}");
        JdbcValueTransactionService crashing =
                new JdbcValueTransactionService(
                        dataSource,
                        checkpoint -> {
                            if (checkpoint
                                    == JdbcValueTransactionService.Checkpoint.AFTER_MUTATION) {
                                throw new SimulatedCrash();
                            }
                        });

        assertThrows(SimulatedCrash.class, () -> crashing.updateItemPayload(request, update));
        assertEquals("{}", success(service.findItem(itemId)).orElseThrow().payloadJson());
        assertEquals(Optional.empty(), success(journal.find(request.transactionId())));

        assertFalse(success(service.updateItemPayload(request, update)).replayed());
        assertTrue(success(service.updateItemPayload(retryRequest(request), update)).replayed());
        ItemLocationRecord current = success(service.findItem(itemId)).orElseThrow();
        assertEquals(2, current.version());
        assertTrue(current.payloadJson().contains("ammo.test.arrow"));
        assertEquals(1, success(audit.findByTransaction(request.transactionId())).size());

        TransactionRequest stale =
                request(
                        "item:payload:stale:" + itemId.value(),
                        JdbcValueTransactionService.ITEM_PAYLOAD_UPDATE,
                        "{}",
                        "{}");
        assertEquals(
                TransactionErrorCode.VALUE_STALE_VERSION,
                failure(service.updateItemPayload(stale, update)));
        assertEquals(Optional.empty(), success(journal.find(stale.transactionId())));
    }

    @Test
    void equipmentSwapRollsBackEveryMoveWhenOneItemIsStale() {
        ItemId incoming = new ItemId(UUID.randomUUID());
        ItemId displaced = new ItemId(UUID.randomUUID());
        ValueLocation inventory = ValueLocation.inventory("slot:2");
        ValueLocation mainHand = ValueLocation.nativeEquipped("MAIN_HAND");
        grantItem(incoming, inventory);
        grantItem(displaced, mainHand);

        ItemLocationMove equipIncoming =
                new ItemLocationMove(
                        incoming,
                        1,
                        Optional.of(CHARACTER),
                        inventory,
                        Optional.of(CHARACTER),
                        mainHand);
        ItemLocationMove staleDisplaced =
                new ItemLocationMove(
                        displaced,
                        2,
                        Optional.of(CHARACTER),
                        mainHand,
                        Optional.of(CHARACTER),
                        inventory);
        TransactionRequest rejected =
                request(
                        "item:equip:stale:" + incoming.value(),
                        JdbcValueTransactionService.ITEM_BATCH_MOVE,
                        "{\"items\":2}",
                        "{\"slot\":\"MAIN_HAND\"}");

        assertEquals(
                TransactionErrorCode.VALUE_STALE_VERSION,
                failure(
                        service.moveItemsAtomically(
                                rejected, List.of(equipIncoming, staleDisplaced))));
        assertEquals(Optional.empty(), success(journal.find(rejected.transactionId())));
        assertEquals(inventory, success(service.findItem(incoming)).orElseThrow().location());
        assertEquals(1, success(service.findItem(incoming)).orElseThrow().version());
        assertEquals(mainHand, success(service.findItem(displaced)).orElseThrow().location());
        assertEquals(1, success(service.findItem(displaced)).orElseThrow().version());

        ItemLocationMove moveDisplaced =
                new ItemLocationMove(
                        displaced,
                        1,
                        Optional.of(CHARACTER),
                        mainHand,
                        Optional.of(CHARACTER),
                        inventory);
        TransactionRequest committed =
                request(
                        "item:equip:commit:" + incoming.value(),
                        JdbcValueTransactionService.ITEM_BATCH_MOVE,
                        "{\"items\":2}",
                        "{\"slot\":\"MAIN_HAND\"}");
        assertFalse(
                success(
                                service.moveItemsAtomically(
                                        committed, List.of(moveDisplaced, equipIncoming)))
                        .replayed());
        assertTrue(
                success(
                                service.moveItemsAtomically(
                                        retryRequest(committed),
                                        List.of(equipIncoming, moveDisplaced)))
                        .replayed());
        assertEquals(mainHand, success(service.findItem(incoming)).orElseThrow().location());
        assertEquals(inventory, success(service.findItem(displaced)).orElseThrow().location());
        assertEquals(2, success(service.findItem(incoming)).orElseThrow().version());
        assertEquals(2, success(service.findItem(displaced)).orElseThrow().version());
    }

    @Test
    void lotGrantAndMovePreserveQuantityAndLineageAcrossCrashRetry() {
        LotId lotId = new LotId(UUID.randomUUID());
        NewLotLocation lot =
                new NewLotLocation(
                        lotId,
                        LOT_DEFINITION,
                        "quality=common",
                        12,
                        Optional.of(CHARACTER),
                        ValueLocation.pendingRewards("lot-reward"),
                        "{\"root\":\"" + lotId.value() + "\"}");
        TransactionRequest grant =
                request(
                        "lot:grant:" + lotId.value(),
                        JdbcValueTransactionService.LOT_GRANT,
                        "{}",
                        "{\"quantity\":12}");
        JdbcValueTransactionService crashing =
                new JdbcValueTransactionService(
                        dataSource,
                        checkpoint -> {
                            if (checkpoint
                                    == JdbcValueTransactionService.Checkpoint.AFTER_PREPARE) {
                                throw new SimulatedCrash();
                            }
                        });

        assertThrows(SimulatedCrash.class, () -> crashing.grantLot(grant, lot));
        assertEquals(Optional.empty(), success(journal.find(grant.transactionId())));
        assertFalse(success(service.grantLot(grant, lot)).replayed());

        LotLocationMove move =
                new LotLocationMove(
                        lotId,
                        1,
                        Optional.of(CHARACTER),
                        lot.location(),
                        Optional.of(CHARACTER),
                        ValueLocation.overflowClaim("lot-overflow"));
        TransactionRequest moveRequest =
                request(
                        "lot:move:" + lotId.value(),
                        JdbcValueTransactionService.LOT_MOVE,
                        "{\"quantity\":12}",
                        "{\"destination\":\"overflow\"}");
        assertFalse(success(service.moveLot(moveRequest, move)).replayed());
        assertTrue(success(service.moveLot(retryRequest(moveRequest), move)).replayed());

        LotLocationRecord current = success(service.findLot(lotId)).orElseThrow();
        assertEquals(12, current.quantity());
        assertTrue(current.lineageJson().contains(lotId.value().toString()));
        assertEquals(ValueLocation.overflowClaim("lot-overflow"), current.location());
        assertEquals(2, current.version());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM commodity_lot"));
    }

    @Test
    void quiverTransferSplitsWithLineageEnforcesCapacityAndRollsBackAcrossCrash() {
        ItemId quiverId = new ItemId(UUID.randomUUID());
        LotId sourceId = new LotId(UUID.randomUUID());
        LotId storedId = new LotId(UUID.randomUUID());
        ValueLocation inventory = ValueLocation.inventory("slot:5");
        ValueLocation quiver = ValueLocation.quiver(quiverId);
        grantItem(quiverId, ValueLocation.virtualEquipped("QUIVER"));
        grantLot(sourceId, inventory, 10);
        TransactionRequest storeRequest =
                request(
                        "quiver:store:" + storedId.value(),
                        JdbcValueTransactionService.LOT_TRANSFER,
                        "{\"quantity\":6}",
                        "{\"lotId\":\"" + storedId.value() + "\"}");
        LotQuantityTransfer store =
                new LotQuantityTransfer(
                        sourceId,
                        storedId,
                        1,
                        10,
                        Optional.of(CHARACTER),
                        inventory,
                        quiver,
                        6,
                        quiverId,
                        1,
                        Optional.of(CHARACTER),
                        ValueLocation.virtualEquipped("QUIVER"),
                        6);
        JdbcValueTransactionService crashing =
                new JdbcValueTransactionService(
                        dataSource,
                        checkpoint -> {
                            if (checkpoint
                                    == JdbcValueTransactionService.Checkpoint.AFTER_MUTATION) {
                                throw new SimulatedCrash();
                            }
                        });

        assertThrows(SimulatedCrash.class, () -> crashing.transferLotQuantity(storeRequest, store));
        assertEquals(10, success(service.findLot(sourceId)).orElseThrow().quantity());
        assertEquals(Optional.empty(), success(service.findLot(storedId)));
        assertEquals(1, success(service.findItem(quiverId)).orElseThrow().version());
        assertEquals(Optional.empty(), success(journal.find(storeRequest.transactionId())));

        assertFalse(success(service.transferLotQuantity(storeRequest, store)).replayed());
        assertTrue(
                success(service.transferLotQuantity(retryRequest(storeRequest), store)).replayed());
        LotLocationRecord source = success(service.findLot(sourceId)).orElseThrow();
        LotLocationRecord stored = success(service.findLot(storedId)).orElseThrow();
        assertEquals(4, source.quantity());
        assertEquals(2, source.version());
        assertEquals(6, stored.quantity());
        assertEquals(quiver, stored.location());
        assertTrue(stored.lineageJson().contains(sourceId.value().toString()));
        assertTrue(stored.lineageJson().contains(storeRequest.transactionId().value().toString()));
        assertEquals(2, success(service.findItem(quiverId)).orElseThrow().version());
        assertEquals(1, success(audit.findByTransaction(storeRequest.transactionId())).size());

        TransactionRequest overflow =
                request(
                        "quiver:overflow:" + sourceId.value(),
                        JdbcValueTransactionService.LOT_TRANSFER,
                        "{\"quantity\":1}",
                        "{}");
        assertEquals(
                TransactionErrorCode.VALUE_CAPACITY_EXCEEDED,
                failure(
                        service.transferLotQuantity(
                                overflow,
                                new LotQuantityTransfer(
                                        sourceId,
                                        new LotId(UUID.randomUUID()),
                                        2,
                                        4,
                                        Optional.of(CHARACTER),
                                        inventory,
                                        quiver,
                                        1,
                                        quiverId,
                                        2,
                                        Optional.of(CHARACTER),
                                        ValueLocation.virtualEquipped("QUIVER"),
                                        6))));
        assertEquals(Optional.empty(), success(journal.find(overflow.transactionId())));
        assertEquals(2, success(service.findItem(quiverId)).orElseThrow().version());
    }

    @Test
    void quiverWithdrawalSplitsIntoOneFreeInventorySlot() {
        ItemId quiverId = new ItemId(UUID.randomUUID());
        LotId storedId = new LotId(UUID.randomUUID());
        LotId withdrawnId = new LotId(UUID.randomUUID());
        ValueLocation quiver = ValueLocation.quiver(quiverId);
        grantItem(quiverId, ValueLocation.virtualEquipped("QUIVER"));
        grantLot(storedId, quiver, 96);
        grantLot(new LotId(UUID.randomUUID()), ValueLocation.inventory("slot:5"), 1);

        TransactionRequest occupied =
                request(
                        "quiver:withdraw:occupied",
                        JdbcValueTransactionService.LOT_TRANSFER,
                        "{\"quantity\":64}",
                        "{}");
        LotQuantityTransfer occupiedTransfer =
                new LotQuantityTransfer(
                        storedId,
                        withdrawnId,
                        1,
                        96,
                        Optional.of(CHARACTER),
                        quiver,
                        ValueLocation.inventory("slot:5"),
                        64,
                        quiverId,
                        1,
                        Optional.of(CHARACTER),
                        ValueLocation.virtualEquipped("QUIVER"),
                        96);
        assertEquals(
                TransactionErrorCode.VALUE_DESTINATION_OCCUPIED,
                failure(service.transferLotQuantity(occupied, occupiedTransfer)));
        assertEquals(1, success(service.findItem(quiverId)).orElseThrow().version());

        TransactionRequest withdraw =
                request(
                        "quiver:withdraw:" + withdrawnId.value(),
                        JdbcValueTransactionService.LOT_TRANSFER,
                        "{\"quantity\":64}",
                        "{\"slot\":7}");
        LotQuantityTransfer transfer =
                new LotQuantityTransfer(
                        storedId,
                        withdrawnId,
                        1,
                        96,
                        Optional.of(CHARACTER),
                        quiver,
                        ValueLocation.inventory("slot:7"),
                        64,
                        quiverId,
                        1,
                        Optional.of(CHARACTER),
                        ValueLocation.virtualEquipped("QUIVER"),
                        96);
        assertFalse(success(service.transferLotQuantity(withdraw, transfer)).replayed());

        LotLocationRecord remaining = success(service.findLot(storedId)).orElseThrow();
        LotLocationRecord withdrawn = success(service.findLot(withdrawnId)).orElseThrow();
        assertEquals(32, remaining.quantity());
        assertEquals(quiver, remaining.location());
        assertEquals(64, withdrawn.quantity());
        assertEquals(ValueLocation.inventory("slot:7"), withdrawn.location());
        assertEquals(2, success(service.findItem(quiverId)).orElseThrow().version());
    }

    @Test
    void lotConsumptionIsReplaySafeAndTombstonesAnEmptyLot() {
        LotId lotId = new LotId(UUID.randomUUID());
        ValueLocation inventory = ValueLocation.inventory("slot:5");
        grantLot(lotId, inventory, 3);
        LotQuantityConsumption firstConsumption =
                new LotQuantityConsumption(lotId, 1, Optional.of(CHARACTER), inventory, 1);
        TransactionRequest first =
                request(
                        "lot:consume:first:" + lotId.value(),
                        JdbcValueTransactionService.LOT_CONSUME,
                        "{\"quantity\":1}",
                        "{\"remaining\":2}");

        assertFalse(success(service.consumeLot(first, firstConsumption)).replayed());
        assertTrue(success(service.consumeLot(retryRequest(first), firstConsumption)).replayed());
        LotLocationRecord remaining = success(service.findLot(lotId)).orElseThrow();
        assertEquals(2, remaining.quantity());
        assertEquals(2, remaining.version());
        assertEquals(inventory, remaining.location());
        List<AuditLogEntry> firstAudit = success(audit.findByTransaction(first.transactionId()));
        assertEquals(1, firstAudit.size());
        assertEquals(AuditSubjectType.LOT, firstAudit.getFirst().subjectType());
        assertEquals(lotId.value(), firstAudit.getFirst().subjectId());

        TransactionRequest insufficient =
                request(
                        "lot:consume:insufficient:" + lotId.value(),
                        JdbcValueTransactionService.LOT_CONSUME,
                        "{\"quantity\":3}",
                        "{}");
        assertEquals(
                TransactionErrorCode.VALUE_INSUFFICIENT_QUANTITY,
                failure(
                        service.consumeLot(
                                insufficient,
                                new LotQuantityConsumption(
                                        lotId, 2, Optional.of(CHARACTER), inventory, 3))));
        assertEquals(Optional.empty(), success(journal.find(insufficient.transactionId())));

        TransactionRequest finalConsumption =
                request(
                        "lot:consume:final:" + lotId.value(),
                        JdbcValueTransactionService.LOT_CONSUME,
                        "{\"quantity\":2}",
                        "{\"remaining\":0}");
        assertFalse(
                success(
                                service.consumeLot(
                                        finalConsumption,
                                        new LotQuantityConsumption(
                                                lotId, 2, Optional.of(CHARACTER), inventory, 2)))
                        .replayed());
        LotLocationRecord destroyed = success(service.findLot(lotId)).orElseThrow();
        assertEquals(0, destroyed.quantity());
        assertEquals(3, destroyed.version());
        assertEquals(ValueLocationType.DESTROYED, destroyed.location().type());
        assertTrue(
                destroyed
                        .location()
                        .reference()
                        .orElseThrow()
                        .contains(finalConsumption.transactionId().value().toString()));
        assertFalse(
                successReconciliation(reconciliation.scan(Duration.ofSeconds(30), 100))
                        .issues()
                        .stream()
                        .anyMatch(issue -> issue.code() == ReconciliationIssueCode.EMPTY_LOT));
    }

    @Test
    void crashAndConcurrentRetryCannotConsumeOneLotTwice() {
        LotId lotId = new LotId(UUID.randomUUID());
        ValueLocation inventory = ValueLocation.inventory("slot:6");
        grantLot(lotId, inventory, 2);
        LotQuantityConsumption consumption =
                new LotQuantityConsumption(lotId, 1, Optional.of(CHARACTER), inventory, 1);
        TransactionRequest crashed =
                request(
                        "lot:consume:crash:" + lotId.value(),
                        JdbcValueTransactionService.LOT_CONSUME,
                        "{\"quantity\":1}",
                        "{}");
        JdbcValueTransactionService crashing =
                new JdbcValueTransactionService(
                        dataSource,
                        checkpoint -> {
                            if (checkpoint
                                    == JdbcValueTransactionService.Checkpoint.AFTER_MUTATION) {
                                throw new SimulatedCrash();
                            }
                        });

        assertThrows(SimulatedCrash.class, () -> crashing.consumeLot(crashed, consumption));
        assertEquals(2, success(service.findLot(lotId)).orElseThrow().quantity());
        assertEquals(Optional.empty(), success(journal.find(crashed.transactionId())));
        assertFalse(success(service.consumeLot(crashed, consumption)).replayed());

        LotQuantityConsumption second =
                new LotQuantityConsumption(lotId, 2, Optional.of(CHARACTER), inventory, 1);
        CompletableFuture<Result<TransactionExecution, TransactionErrorCode>> first =
                CompletableFuture.supplyAsync(
                        () ->
                                service.consumeLot(
                                        request(
                                                "lot:consume:race-a:" + lotId.value(),
                                                JdbcValueTransactionService.LOT_CONSUME,
                                                "{\"quantity\":1}",
                                                "{}"),
                                        second));
        CompletableFuture<Result<TransactionExecution, TransactionErrorCode>> competing =
                CompletableFuture.supplyAsync(
                        () ->
                                service.consumeLot(
                                        request(
                                                "lot:consume:race-b:" + lotId.value(),
                                                JdbcValueTransactionService.LOT_CONSUME,
                                                "{\"quantity\":1}",
                                                "{}"),
                                        second));
        List<Result<TransactionExecution, TransactionErrorCode>> results =
                List.of(first.join(), competing.join());

        assertEquals(1, results.stream().filter(Result::isSuccess).count());
        assertEquals(
                1,
                results.stream()
                        .filter(result -> !result.isSuccess())
                        .map(JdbcValueTransactionServiceIntegrationTest::failure)
                        .filter(TransactionErrorCode.VALUE_STALE_VERSION::equals)
                        .count());
        LotLocationRecord destroyed = success(service.findLot(lotId)).orElseThrow();
        assertEquals(0, destroyed.quantity());
        assertEquals(ValueLocationType.DESTROYED, destroyed.location().type());
    }

    @Test
    void ownedValueQueriesReturnOnlyTheRequestedCharacterInStableLocationOrder() {
        CharacterId otherCharacter = new CharacterId(UUID.randomUUID());
        ItemId secondItem = new ItemId(UUID.randomUUID());
        ItemId firstItem = new ItemId(UUID.randomUUID());
        LotId ownedLot = new LotId(UUID.randomUUID());
        LotId foreignLot = new LotId(UUID.randomUUID());

        grantItem(secondItem, ValueLocation.inventory("slot:8"));
        grantItem(firstItem, ValueLocation.inventory("slot:2"));
        grantItemFor(otherCharacter, new ItemId(UUID.randomUUID()), "slot:1");
        grantLotFor(CHARACTER, ownedLot, "slot:3");
        grantLotFor(otherCharacter, foreignLot, "slot:4");

        List<ItemLocationRecord> items = success(service.findItemsOwnedBy(CHARACTER));
        List<LotLocationRecord> lots = success(service.findLotsOwnedBy(CHARACTER));

        assertEquals(2, items.size());
        assertEquals(ValueLocation.inventory("slot:2"), items.get(0).location());
        assertEquals(ValueLocation.inventory("slot:8"), items.get(1).location());
        assertEquals(List.of(ownedLot), lots.stream().map(LotLocationRecord::lotId).toList());
    }

    @Test
    void reconciliationReportsAnomaliesWithoutMovingOrDeletingValue() throws Exception {
        TransactionRequest stuck =
                request("transaction:stuck", "test.stuck", "{}", "{\"expected\":\"value\"}");
        success(journal.prepare(stuck));
        ItemId itemId = new ItemId(UUID.randomUUID());
        LotId lotId = new LotId(UUID.randomUUID());

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement age =
                    connection.prepareStatement(
                            """
                            UPDATE transaction_journal
                            SET created_at = CURRENT_TIMESTAMP - INTERVAL '10 minutes',
                                updated_at = CURRENT_TIMESTAMP - INTERVAL '5 minutes'
                            WHERE transaction_id = ?
                            """)) {
                age.setObject(1, stuck.transactionId().value());
                assertEquals(1, age.executeUpdate());
            }
            try (PreparedStatement item =
                    connection.prepareStatement(
                            """
                            INSERT INTO item_instance(
                                item_uuid, definition_id, owner_character_id,
                                location_type, location_ref, payload, content_version,
                                version, last_transaction_id, created_at, updated_at
                            )
                            VALUES (?, ?, NULL, 'LEGACY_VOID', NULL, '{}'::jsonb, ?,
                                    1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """)) {
                item.setObject(1, itemId.value());
                item.setString(2, ITEM_DEFINITION.value());
                item.setString(3, stuck.contentVersion());
                item.setObject(4, stuck.transactionId().value());
                assertEquals(1, item.executeUpdate());
            }
            try (PreparedStatement lot =
                    connection.prepareStatement(
                            """
                            INSERT INTO commodity_lot(
                                lot_uuid, definition_id, variant, quantity, owner_character_id,
                                location_type, location_ref, lineage, content_version,
                                version, last_transaction_id, created_at, updated_at
                            )
                            VALUES (?, ?, 'legacy', 0, NULL, 'LEGACY_VOID', NULL,
                                    '{}'::jsonb, ?, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """)) {
                lot.setObject(1, lotId.value());
                lot.setString(2, LOT_DEFINITION.value());
                lot.setString(3, stuck.contentVersion());
                lot.setObject(4, stuck.transactionId().value());
                assertEquals(1, lot.executeUpdate());
            }
        }

        ReconciliationReport report =
                successReconciliation(reconciliation.scan(Duration.ofSeconds(30), 100));
        EnumSet<ReconciliationIssueCode> codes = EnumSet.noneOf(ReconciliationIssueCode.class);
        report.issues().forEach(issue -> codes.add(issue.code()));

        assertTrue(codes.contains(ReconciliationIssueCode.STALE_PREPARED_TRANSACTION));
        assertTrue(codes.contains(ReconciliationIssueCode.ITEM_UNCOMMITTED_TRANSACTION));
        assertTrue(codes.contains(ReconciliationIssueCode.LOT_UNCOMMITTED_TRANSACTION));
        assertTrue(codes.contains(ReconciliationIssueCode.UNKNOWN_ITEM_LOCATION));
        assertTrue(codes.contains(ReconciliationIssueCode.UNKNOWN_LOT_LOCATION));
        assertTrue(codes.contains(ReconciliationIssueCode.ITEM_MISSING_OWNER));
        assertTrue(codes.contains(ReconciliationIssueCode.LOT_MISSING_OWNER));
        assertTrue(codes.contains(ReconciliationIssueCode.EMPTY_LOT));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM item_instance"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM commodity_lot"));
        assertEquals(
                TransactionState.PREPARED,
                success(journal.find(stuck.transactionId())).orElseThrow().state());
    }

    private void grantItem(ItemId itemId, ValueLocation location) {
        NewItemLocation item =
                new NewItemLocation(
                        itemId, ITEM_DEFINITION, Optional.of(CHARACTER), location, "{}");
        success(
                service.grantItem(
                        request(
                                "item:setup:" + itemId.value(),
                                JdbcValueTransactionService.ITEM_GRANT,
                                "{}",
                                "{\"itemId\":\"" + itemId.value() + "\"}"),
                        item));
    }

    private void grantItemFor(CharacterId characterId, ItemId itemId, String slot) {
        NewItemLocation item =
                new NewItemLocation(
                        itemId,
                        ITEM_DEFINITION,
                        Optional.of(characterId),
                        ValueLocation.inventory(slot),
                        "{}");
        TransactionRequest request =
                new TransactionRequest(
                        transactionId(),
                        "item:setup:" + itemId.value(),
                        Optional.of(characterId),
                        Optional.of(SESSION),
                        JdbcValueTransactionService.ITEM_GRANT,
                        "{}",
                        "{\"itemId\":\"" + itemId.value() + "\"}",
                        "content-test-1");
        success(service.grantItem(request, item));
    }

    private void grantLotFor(CharacterId characterId, LotId lotId, String slot) {
        NewLotLocation lot =
                new NewLotLocation(
                        lotId,
                        LOT_DEFINITION,
                        "quality=common",
                        5,
                        Optional.of(characterId),
                        ValueLocation.inventory(slot),
                        "{\"root\":\"" + lotId.value() + "\"}");
        TransactionRequest request =
                new TransactionRequest(
                        transactionId(),
                        "lot:setup:" + lotId.value(),
                        Optional.of(characterId),
                        Optional.of(SESSION),
                        JdbcValueTransactionService.LOT_GRANT,
                        "{}",
                        "{\"lotId\":\"" + lotId.value() + "\"}",
                        "content-test-1");
        success(service.grantLot(request, lot));
    }

    private void grantLot(LotId lotId, ValueLocation location, long quantity) {
        NewLotLocation lot =
                new NewLotLocation(
                        lotId,
                        LOT_DEFINITION,
                        "quality=common",
                        quantity,
                        Optional.of(CHARACTER),
                        location,
                        "{\"root\":\"" + lotId.value() + "\"}");
        success(
                service.grantLot(
                        request(
                                "lot:setup:" + lotId.value(),
                                JdbcValueTransactionService.LOT_GRANT,
                                "{}",
                                "{\"quantity\":" + quantity + "}"),
                        lot));
    }

    private static TransactionRequest request(
            String key, String operation, String inputs, String outputs) {
        return TransactionRequest.forCharacter(
                transactionId(),
                key,
                CHARACTER,
                SESSION,
                operation,
                inputs,
                outputs,
                "content-test-1");
    }

    private static TransactionRequest retryRequest(TransactionRequest original) {
        return new TransactionRequest(
                transactionId(),
                original.idempotencyKey(),
                original.characterId(),
                original.sessionId(),
                original.operationType(),
                original.reservedInputsJson(),
                original.intendedOutputsJson(),
                original.contentVersion());
    }

    private static TransactionId transactionId() {
        return new TransactionId(UUID.randomUUID());
    }

    private static <T> T success(Result<T, TransactionErrorCode> result) {
        assertTrue(result.isSuccess(), () -> failureDetail(result));
        return ((Result.Success<T, TransactionErrorCode>) result).value();
    }

    private static <T> TransactionErrorCode failure(Result<T, TransactionErrorCode> result) {
        assertFalse(result.isSuccess());
        return ((Result.Failure<T, TransactionErrorCode>) result).error();
    }

    private static <T> String failureDetail(Result<T, TransactionErrorCode> result) {
        if (result instanceof Result.Failure<T, TransactionErrorCode> failure) {
            return failure.error() + ": " + failure.detail();
        }
        return "";
    }

    private static ReconciliationReport successReconciliation(
            Result<ReconciliationReport, ReconciliationErrorCode> result) {
        assertTrue(
                result.isSuccess(),
                () -> {
                    if (result
                            instanceof
                            Result.Failure<ReconciliationReport, ReconciliationErrorCode> failure) {
                        return failure.error() + ": " + failure.detail();
                    }
                    return "";
                });
        return ((Result.Success<ReconciliationReport, ReconciliationErrorCode>) result).value();
    }

    private int scalarInt(String sql) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(sql)) {
            row.next();
            return row.getInt(1);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class SimulatedCrash extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
