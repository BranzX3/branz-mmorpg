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
import com.branz.mmorpg.lifeskills.node.ResourceNodeId;
import com.branz.mmorpg.persistence.migration.ClasspathMigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationErrorCode;
import com.branz.mmorpg.persistence.migration.PostgresMigrationRunner;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcResourceNodeStateRepositoryIntegrationTest {
    private static final CharacterId ACTOR = new CharacterId(UUID.randomUUID());
    private static final SessionId SESSION = new SessionId(UUID.randomUUID());
    private static final DefinitionId NODE_DEFINITION = DefinitionId.of("node.mining.test_ore");
    private static final DefinitionId TOOL_DEFINITION = DefinitionId.of("item.tool.test_pickaxe");
    private static final DefinitionId ORE_DEFINITION = DefinitionId.of("material.test.ore");
    private static final ValueLocation TOOL_LOCATION = ValueLocation.nativeEquipped("MAIN_HAND");
    private static final String CONTENT_VERSION = "test-content-v1";

    private EmbeddedPostgres postgres;
    private DataSource dataSource;
    private JdbcResourceNodeStateRepository repository;
    private JdbcValueTransactionService values;
    private ResourceNodeId nodeId;
    private ItemId toolId;

    @BeforeAll
    void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
        dataSource = postgres.getPostgresDatabase();
    }

    @AfterAll
    void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
        Result<MigrationCatalog, MigrationErrorCode> loaded =
                ClasspathMigrationCatalog.loadDefault();
        assertTrue(loaded.isSuccess());
        assertTrue(
                new PostgresMigrationRunner(dataSource)
                        .migrate(
                                ((Result.Success<MigrationCatalog, MigrationErrorCode>) loaded)
                                        .value())
                        .isSuccess());
        repository = new JdbcResourceNodeStateRepository(dataSource);
        values = new JdbcValueTransactionService(dataSource);
        nodeId = new ResourceNodeId(UUID.randomUUID());
        toolId = new ItemId(UUID.randomUUID());
        grantTool();
    }

    @Test
    void reservationAndHarvestCommitEveryValueExactlyOnce() throws Exception {
        UUID reservationId = UUID.randomUUID();
        String reservedTool = reservedTool(reservationId);
        ResourceNodeStateCommit reserve = reserveCommit(reservationId, reservedTool);
        TransactionRequest reserveRequest = request("reserve", UUID.randomUUID(), "{}", "{}");

        ResourceNodeStateCommitExecution reserved =
                success(repository.commit(reserveRequest, reserve));
        assertFalse(reserved.transaction().replayed());
        assertEquals(1, reserved.node().version());
        assertEquals(2, reserved.tool().orElseThrow().version());
        assertTrue(reserved.characterState().isEmpty());
        assertTrue(reserved.outputLots().isEmpty());

        ResourceNodeStateCommitExecution reserveReplay =
                success(repository.commit(reserveRequest, reserve));
        assertTrue(reserveReplay.transaction().replayed());
        assertEquals(1, reserveReplay.node().version());
        assertEquals(2, reserveReplay.tool().orElseThrow().version());

        LotId outputId = new LotId(UUID.randomUUID());
        ResourceNodeStateCommit harvest = harvestCommit(reservedTool, outputId, 2);
        TransactionRequest harvestRequest =
                request(
                        "harvest",
                        UUID.randomUUID(),
                        "{\"reservationId\":\"" + reservationId + "\"}",
                        "{\"lotId\":\"" + outputId.value() + "\"}");
        ResourceNodeStateCommitExecution harvested =
                success(repository.commit(harvestRequest, harvest));
        assertEquals(2, harvested.node().version());
        assertEquals("DEPLETED", harvested.node().phase());
        assertEquals(1, harvested.characterState().orElseThrow().version());
        assertEquals(3, harvested.tool().orElseThrow().version());
        assertEquals("{\"durability\": 8}", harvested.tool().orElseThrow().payloadJson());
        assertEquals(
                List.of(outputId),
                harvested.outputLots().stream().map(LotLocationRecord::lotId).toList());
        assertEquals(4, harvested.outputLots().getFirst().quantity());
        assertEquals(harvestRequest.transactionId(), harvested.node().lastTransactionId());
        assertEquals(
                harvestRequest.transactionId(),
                harvested.characterState().orElseThrow().lastTransactionId());
        assertEquals(
                harvestRequest.transactionId(), harvested.tool().orElseThrow().lastTransactionId());
        assertEquals(
                harvestRequest.transactionId(),
                harvested.outputLots().getFirst().lastTransactionId());

        ResourceNodeStateCommitExecution replayed =
                success(repository.commit(harvestRequest, harvest));
        assertTrue(replayed.transaction().replayed());
        assertEquals(2, replayed.node().version());
        assertEquals(3, replayed.tool().orElseThrow().version());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM commodity_lot"));
        assertEquals(3, scalarInt("SELECT COUNT(*) FROM transaction_journal"));
        assertEquals(3, scalarInt("SELECT COUNT(*) FROM audit_log"));
        assertEquals(
                List.of(nodeId),
                success(repository.findRecoverable()).stream()
                        .map(ResourceNodeStateRecord::nodeId)
                        .toList());

        ResourceNodeStateCommit recovery =
                new ResourceNodeStateCommit(
                        ResourceNodeCommitKind.RECOVER,
                        nodeId,
                        NODE_DEFINITION,
                        "AVAILABLE",
                        2,
                        "{\"phase\":\"AVAILABLE\",\"charges\":1}",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of());
        success(repository.commit(systemRequest("recover", UUID.randomUUID()), recovery));
        assertTrue(success(repository.findRecoverable()).isEmpty());
    }

    @Test
    void staleToolRollsBackNodeProgressionOutputJournalAndAudit() throws Exception {
        UUID reservationId = UUID.randomUUID();
        String reservedTool = reservedTool(reservationId);
        success(
                repository.commit(
                        request("reserve", UUID.randomUUID(), "{}", "{}"),
                        reserveCommit(reservationId, reservedTool)));
        LotId outputId = new LotId(UUID.randomUUID());
        ResourceNodeStateCommit staleHarvest = harvestCommit(reservedTool, outputId, 99);

        assertEquals(
                TransactionErrorCode.VALUE_STALE_VERSION,
                failure(
                        repository.commit(
                                request("harvest-stale", UUID.randomUUID(), "{}", "{}"),
                                staleHarvest)));
        assertEquals(1, success(repository.find(nodeId)).orElseThrow().version());
        assertEquals("RESERVED", success(repository.find(nodeId)).orElseThrow().phase());
        assertTrue(success(repository.findCharacterState(ACTOR)).isEmpty());
        assertTrue(success(values.findLot(outputId)).isEmpty());
        assertEquals(2, success(values.findItem(toolId)).orElseThrow().version());
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM transaction_journal"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM audit_log"));
    }

    @Test
    void staleNodeAndChangedTransactionReplayFailClosed() {
        UUID reservationId = UUID.randomUUID();
        String reservedTool = reservedTool(reservationId);
        ResourceNodeStateCommit reserve = reserveCommit(reservationId, reservedTool);
        UUID transactionId = UUID.randomUUID();
        TransactionRequest request = request("reserve", transactionId, "{}", "{}");
        success(repository.commit(request, reserve));

        ResourceNodeStateCommit stale =
                new ResourceNodeStateCommit(
                        ResourceNodeCommitKind.CANCEL,
                        nodeId,
                        NODE_DEFINITION,
                        "AVAILABLE",
                        0,
                        "{\"phase\":\"AVAILABLE\"}",
                        Optional.of(ACTOR),
                        Optional.empty(),
                        Optional.of(toolUpdate(2, reservedTool, "{\"durability\":10}")),
                        List.of());
        assertEquals(
                TransactionErrorCode.VALUE_STALE_VERSION,
                failure(repository.commit(request("stale", UUID.randomUUID(), "{}", "{}"), stale)));

        TransactionRequest changed =
                request("reserve", transactionId, "{}", "{\"different\":true}");
        assertEquals(
                TransactionErrorCode.TRANSACTION_IDEMPOTENCY_CONFLICT,
                failure(repository.commit(changed, reserve)));
        assertEquals(1, success(repository.find(nodeId)).orElseThrow().version());
    }

    @Test
    void crashDuringReservationRollsBackNodeToolJournalAndAuditThenRetries() throws Exception {
        UUID reservationId = UUID.randomUUID();
        String reservedTool = reservedTool(reservationId);
        ResourceNodeStateCommit reserve = reserveCommit(reservationId, reservedTool);
        TransactionRequest request = request("reserve-crash", UUID.randomUUID(), "{}", "{}");
        JdbcResourceNodeStateRepository crashing =
                new JdbcResourceNodeStateRepository(
                        dataSource,
                        checkpoint -> {
                            if (checkpoint
                                    == JdbcResourceNodeStateRepository.Checkpoint
                                            .AFTER_TOOL_MUTATION) {
                                throw new SimulatedCrash();
                            }
                        });

        assertThrows(SimulatedCrash.class, () -> crashing.commit(request, reserve));
        assertTrue(success(repository.find(nodeId)).isEmpty());
        assertEquals(1, success(values.findItem(toolId)).orElseThrow().version());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM transaction_journal"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM audit_log"));

        ResourceNodeStateCommitExecution retried = success(repository.commit(request, reserve));
        assertFalse(retried.transaction().replayed());
        assertEquals(1, retried.node().version());
        assertEquals(2, retried.tool().orElseThrow().version());
    }

    @Test
    void crashAfterHarvestOutputsRollsBackEveryMutationThenRetriesExactlyOnce() throws Exception {
        UUID reservationId = UUID.randomUUID();
        String reservedTool = reservedTool(reservationId);
        success(
                repository.commit(
                        request("reserve", UUID.randomUUID(), "{}", "{}"),
                        reserveCommit(reservationId, reservedTool)));
        LotId outputId = new LotId(UUID.randomUUID());
        ResourceNodeStateCommit harvest = harvestCommit(reservedTool, outputId, 2);
        TransactionRequest request = request("harvest-crash", UUID.randomUUID(), "{}", "{}");
        JdbcResourceNodeStateRepository crashing =
                new JdbcResourceNodeStateRepository(
                        dataSource,
                        checkpoint -> {
                            if (checkpoint
                                    == JdbcResourceNodeStateRepository.Checkpoint
                                            .AFTER_OUTPUT_MUTATION) {
                                throw new SimulatedCrash();
                            }
                        });

        assertThrows(SimulatedCrash.class, () -> crashing.commit(request, harvest));
        assertEquals(1, success(repository.find(nodeId)).orElseThrow().version());
        assertTrue(success(repository.findCharacterState(ACTOR)).isEmpty());
        assertEquals(2, success(values.findItem(toolId)).orElseThrow().version());
        assertTrue(success(values.findLot(outputId)).isEmpty());
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM transaction_journal"));

        ResourceNodeStateCommitExecution retried = success(repository.commit(request, harvest));
        assertFalse(retried.transaction().replayed());
        assertEquals(2, retried.node().version());
        assertEquals(1, retried.outputLots().size());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM commodity_lot"));
    }

    private void grantTool() {
        NewItemLocation tool =
                new NewItemLocation(
                        toolId,
                        TOOL_DEFINITION,
                        Optional.of(ACTOR),
                        TOOL_LOCATION,
                        "{\"durability\":10}");
        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(UUID.randomUUID()),
                        "test:tool:" + toolId.value(),
                        ACTOR,
                        SESSION,
                        JdbcValueTransactionService.ITEM_GRANT,
                        "{}",
                        "{\"toolId\":\"" + toolId.value() + "\"}",
                        CONTENT_VERSION);
        success(values.grantItem(request, tool));
    }

    private ResourceNodeStateCommit reserveCommit(UUID reservationId, String reservedTool) {
        return new ResourceNodeStateCommit(
                ResourceNodeCommitKind.RESERVE,
                nodeId,
                NODE_DEFINITION,
                "RESERVED",
                0,
                "{\"phase\":\"RESERVED\",\"reservationId\":\"" + reservationId + "\"}",
                Optional.of(ACTOR),
                Optional.empty(),
                Optional.of(toolUpdate(1, "{\"durability\":10}", reservedTool)),
                List.of());
    }

    private ResourceNodeStateCommit harvestCommit(
            String reservedTool, LotId outputId, long expectedToolVersion) {
        return new ResourceNodeStateCommit(
                ResourceNodeCommitKind.HARVEST,
                nodeId,
                NODE_DEFINITION,
                "DEPLETED",
                1,
                "{\"phase\":\"DEPLETED\",\"recoversAt\":\"2026-08-01T00:01:00Z\"}",
                Optional.of(ACTOR),
                Optional.of(
                        new CharacterLifeskillStateMutation(
                                ACTOR, 0, "{\"focus\":95,\"rankEvidence\":4.0}")),
                Optional.of(toolUpdate(expectedToolVersion, reservedTool, "{\"durability\":8}")),
                List.of(
                        new NewLotLocation(
                                outputId,
                                ORE_DEFINITION,
                                "standard",
                                4,
                                Optional.of(ACTOR),
                                ValueLocation.pendingRewards("node:" + nodeId.value()),
                                "{\"nodeId\":\"" + nodeId.value() + "\"}")));
    }

    private ItemPayloadUpdate toolUpdate(long version, String expected, String replacement) {
        return new ItemPayloadUpdate(
                toolId, version, Optional.of(ACTOR), TOOL_LOCATION, expected, replacement);
    }

    private static String reservedTool(UUID reservationId) {
        return "{\"durability\":10,\"nodeReservation\":\"" + reservationId + "\"}";
    }

    private static TransactionRequest request(
            String action, UUID transactionId, String reserved, String outputs) {
        return TransactionRequest.forCharacter(
                new TransactionId(transactionId),
                "node:" + action + ":" + transactionId,
                ACTOR,
                SESSION,
                JdbcResourceNodeStateRepository.RESOURCE_NODE_STATE_COMMIT,
                reserved,
                outputs,
                CONTENT_VERSION);
    }

    private static TransactionRequest systemRequest(String action, UUID transactionId) {
        return TransactionRequest.system(
                new TransactionId(transactionId),
                "node:" + action + ":" + transactionId,
                JdbcResourceNodeStateRepository.RESOURCE_NODE_STATE_COMMIT,
                "{}",
                "{}",
                CONTENT_VERSION);
    }

    private int scalarInt(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(sql)) {
            row.next();
            return row.getInt(1);
        }
    }

    private static <T> T success(Result<T, TransactionErrorCode> result) {
        assertTrue(
                result.isSuccess(),
                () ->
                        result instanceof Result.Failure<T, TransactionErrorCode> failure
                                ? failure.error().code() + ": " + failure.detail()
                                : "");
        return ((Result.Success<T, TransactionErrorCode>) result).value();
    }

    private static TransactionErrorCode failure(Result<?, TransactionErrorCode> result) {
        assertFalse(result.isSuccess());
        return ((Result.Failure<?, TransactionErrorCode>) result).error();
    }

    private static final class SimulatedCrash extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
