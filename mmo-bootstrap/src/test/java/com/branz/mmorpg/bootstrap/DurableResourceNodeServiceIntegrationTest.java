package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.SessionId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.ErrorCode;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.content.snapshot.ContentSnapshotLoader;
import com.branz.mmorpg.lifeskills.node.ResourceNodeEngine;
import com.branz.mmorpg.lifeskills.node.ResourceNodePhase;
import com.branz.mmorpg.persistence.lease.CharacterLease;
import com.branz.mmorpg.persistence.lease.ServerInstanceId;
import com.branz.mmorpg.persistence.migration.ClasspathMigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationCatalog;
import com.branz.mmorpg.persistence.migration.PostgresMigrationRunner;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.JdbcResourceNodeStateRepository;
import com.branz.mmorpg.persistence.transaction.JdbcValueTransactionService;
import com.branz.mmorpg.persistence.transaction.NewItemLocation;
import com.branz.mmorpg.persistence.transaction.TransactionExecution;
import com.branz.mmorpg.persistence.transaction.TransactionRequest;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import com.branz.mmorpg.persistence.transaction.ValueLocationType;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
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
class DurableResourceNodeServiceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");
    private static final String CONTENT_VERSION = "v1.milestone-1.example.4";

    private EmbeddedPostgres postgres;
    private DataSource dataSource;
    private JdbcValueTransactionService values;
    private JdbcResourceNodeStateRepository nodes;
    private CompiledResourceNode content;
    private DurableResourceNodeService service;
    private LoadedCharacterSession session;
    private ItemLocationRecord tool;

    @BeforeAll
    void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
        dataSource = postgres.getPostgresDatabase();
        Path fixture = Path.of("..", "example-content", "milestone-1").toAbsolutePath().normalize();
        ContentSnapshot snapshot = success(new ContentSnapshotLoader().load(fixture));
        content = ResourceNodeContentCompiler.compileFirst(snapshot).orElseThrow();
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
        MigrationCatalog catalog = success(ClasspathMigrationCatalog.loadDefault());
        assertTrue(new PostgresMigrationRunner(dataSource).migrate(catalog).isSuccess());
        values = new JdbcValueTransactionService(dataSource);
        nodes = new JdbcResourceNodeStateRepository(dataSource);
        service = new DurableResourceNodeService(nodes, values, content, CONTENT_VERSION, 100);
        session = session();
        tool = grantTool(session.characterId());
        session =
                session.withSnapshot(
                        PersistentCharacterSnapshotMapper.map(
                                List.of(tool),
                                List.of(),
                                Optional.empty(),
                                List.of(),
                                List.of(),
                                Optional.empty(),
                                Optional.empty()));
    }

    @Test
    void reserveAndHarvestComposeNodeToolProgressionAndPendingReward() {
        LiveResourceNodeReservation reservation = reserve(3);

        LiveResourceNodeHarvest harvest =
                success(
                        service.harvest(
                                session,
                                reservation,
                                reservation.commitAtTick(),
                                NOW.plusSeconds(2)));

        assertEquals(99, harvest.durabilityRemaining());
        assertEquals(97, harvest.lifeskillState().focus().focus());
        assertEquals("Trainee II", harvest.lifeskillState().rank().rank().displayName());
        assertEquals(1, harvest.execution().outputLots().size());
        assertEquals(
                ValueLocationType.PENDING_REWARDS,
                harvest.execution().outputLots().getFirst().location().type());
        assertEquals(
                content.outputDefinitionId(),
                harvest.execution().outputLots().getFirst().definitionId());
        assertEquals(
                ResourceNodePhase.DEPLETED,
                new ResourceNodeEngine()
                        .slotFor(
                                content.definition(),
                                service.decode(harvest.execution().node()),
                                session.characterId())
                        .phase());
        ItemLocationRecord persistedTool = success(values.findItem(tool.itemId())).orElseThrow();
        assertEquals(99, service.toolDurability(persistedTool));
        assertFalse(
                new ResourceNodeToolPayloadCodec()
                        .reservation(persistedTool.payloadJson())
                        .isPresent());

        LiveResourceNodeHarvest replay =
                success(
                        service.harvest(
                                session,
                                reservation,
                                reservation.commitAtTick(),
                                NOW.plusSeconds(2)));
        assertTrue(replay.execution().transaction().replayed());
        assertEquals(
                1,
                success(values.findLotsOwnedBy(session.characterId())).stream()
                        .filter(lot -> lot.location().type() == ValueLocationType.PENDING_REWARDS)
                        .count());
    }

    @Test
    void restartRecoveryReleasesPrecommitNodeAndExactTool() {
        reserve(0);

        assertEquals(1, success(service.reconcile(NOW.plusSeconds(1), true)));

        ItemLocationRecord recoveredTool = success(values.findItem(tool.itemId())).orElseThrow();
        assertTrue(
                new ResourceNodeToolPayloadCodec()
                        .reservation(recoveredTool.payloadJson())
                        .isEmpty());
        var node = success(service.findNode()).orElseThrow();
        assertEquals(
                ResourceNodePhase.AVAILABLE,
                new ResourceNodeEngine()
                        .slotFor(content.definition(), service.decode(node), session.characterId())
                        .phase());
    }

    private LiveResourceNodeReservation reserve(int focusCost) {
        return success(
                service.reserve(
                        session,
                        tool,
                        focusCost,
                        100,
                        NOW,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID()));
    }

    private ItemLocationRecord grantTool(CharacterId characterId) {
        ItemId itemId = new ItemId(UUID.randomUUID());
        UUID transactionId = UUID.randomUUID();
        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(transactionId),
                        "test-tool:" + transactionId,
                        characterId,
                        session.sessionId(),
                        JdbcValueTransactionService.ITEM_GRANT,
                        "{}",
                        "{}",
                        CONTENT_VERSION);
        TransactionExecution granted =
                success(
                        values.grantItem(
                                request,
                                new NewItemLocation(
                                        itemId,
                                        content.toolDefinitionId(),
                                        Optional.of(characterId),
                                        ValueLocation.inventory("slot:1"),
                                        "{\"displayRevision\":1,\"testProvenance\":\"dev:test\"}")));
        assertFalse(granted.replayed());
        return success(values.findItem(itemId)).orElseThrow();
    }

    private static LoadedCharacterSession session() {
        CharacterId characterId = new CharacterId(UUID.randomUUID());
        SessionId sessionId = new SessionId(UUID.randomUUID());
        CharacterLease lease =
                new CharacterLease(
                        characterId,
                        new ServerInstanceId("node-lab-test"),
                        sessionId,
                        1,
                        NOW,
                        NOW,
                        NOW.plus(Duration.ofMinutes(5)));
        return new LoadedCharacterSession(
                characterId,
                sessionId,
                lease,
                PersistentCharacterSnapshotMapper.map(
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty()));
    }

    private static <T, E extends ErrorCode> T success(Result<T, E> result) {
        assertTrue(
                result.isSuccess(),
                () -> result instanceof Result.Failure<T, E> failure ? failure.detail() : "");
        return ((Result.Success<T, E>) result).value();
    }
}
