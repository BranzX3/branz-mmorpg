package com.branz.mmorpg.persistence.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcCharacterExpeditionStateRepositoryIntegrationTest {
    private EmbeddedPostgres postgres;
    private DataSource dataSource;

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
    }

    @Test
    void commitsReplaysRejectsConflictAndReloadsAcrossRepositoryRestart() throws Exception {
        CharacterId characterId = new CharacterId(UUID.randomUUID());
        SessionId sessionId = new SessionId(UUID.randomUUID());
        JdbcCharacterExpeditionStateRepository repository =
                new JdbcCharacterExpeditionStateRepository(dataSource);
        assertTrue(success(repository.find(characterId)).isEmpty());

        String firstPayload = "{\"schemaVersion\":1,\"flask\":{\"charges\":4},\"ailments\":[]}";
        UUID firstId = UUID.randomUUID();
        TransactionRequest firstRequest = request(firstId, characterId, sessionId, 0, firstPayload);
        CharacterExpeditionStateCommitExecution first =
                success(
                        repository.commit(
                                firstRequest,
                                new CharacterExpeditionStateCommit(characterId, 0, firstPayload)));
        assertEquals(1, first.record().version());
        assertFalse(first.transaction().replayed());

        CharacterExpeditionStateCommitExecution replay =
                success(
                        repository.commit(
                                firstRequest,
                                new CharacterExpeditionStateCommit(characterId, 0, firstPayload)));
        assertTrue(replay.transaction().replayed());

        String secondPayload = "{\"schemaVersion\":1,\"flask\":{\"charges\":3},\"ailments\":[]}";
        CharacterExpeditionStateCommitExecution second =
                success(
                        repository.commit(
                                request(
                                        UUID.randomUUID(),
                                        characterId,
                                        sessionId,
                                        1,
                                        secondPayload),
                                new CharacterExpeditionStateCommit(characterId, 1, secondPayload)));
        assertEquals(2, second.record().version());

        JdbcCharacterExpeditionStateRepository restarted =
                new JdbcCharacterExpeditionStateRepository(dataSource);
        CharacterExpeditionStateRecord restored =
                success(restarted.find(characterId)).orElseThrow();
        assertEquals(2, restored.version());
        assertTrue(restored.payloadJson().contains("\"charges\": 3"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM audit_log"));

        Result<CharacterExpeditionStateCommitExecution, TransactionErrorCode> stale =
                restarted.commit(
                        request(UUID.randomUUID(), characterId, sessionId, 1, firstPayload),
                        new CharacterExpeditionStateCommit(characterId, 1, firstPayload));
        assertFalse(stale.isSuccess());
        assertEquals(
                TransactionErrorCode.VALUE_STALE_VERSION,
                ((Result.Failure<CharacterExpeditionStateCommitExecution, TransactionErrorCode>)
                                stale)
                        .error());
    }

    @Test
    void flaskPreparationConsumesStockAtomicallyAndReplayDoesNotConsumeAgain() {
        CharacterId characterId = new CharacterId(UUID.randomUUID());
        SessionId sessionId = new SessionId(UUID.randomUUID());
        DefinitionId infusionStock = DefinitionId.of("material.infusion_stock");
        LotId stockLotId = new LotId(UUID.randomUUID());
        JdbcValueTransactionService values = new JdbcValueTransactionService(dataSource);
        assertTrue(
                values.grantLot(
                                TransactionRequest.forCharacter(
                                        new TransactionId(UUID.randomUUID()),
                                        "grant-infusion:" + stockLotId.value(),
                                        characterId,
                                        sessionId,
                                        JdbcValueTransactionService.LOT_GRANT,
                                        "{}",
                                        "{\"quantity\":4}",
                                        "test-content-v1"),
                                new NewLotLocation(
                                        stockLotId,
                                        infusionStock,
                                        "default",
                                        4,
                                        java.util.Optional.of(characterId),
                                        ValueLocation.inventory("slot:4"),
                                        "{}"))
                        .isSuccess());
        LotLocationRecord original = success(values.findLot(stockLotId)).orElseThrow();
        String payload = "{\"schemaVersion\":2,\"flask\":{\"charges\":3}}";
        UUID operationId = UUID.randomUUID();
        TransactionRequest request =
                TransactionRequest.forCharacter(
                        new TransactionId(operationId),
                        "flask-rest:" + operationId,
                        characterId,
                        sessionId,
                        JdbcCharacterExpeditionStateRepository.CHARACTER_FLASK_PREPARATION_COMMIT,
                        "{\"stockConsumed\":3}",
                        payload,
                        "test-content-v1");
        CharacterFlaskPreparationCommit commit =
                new CharacterFlaskPreparationCommit(
                        characterId,
                        0,
                        payload,
                        infusionStock,
                        java.util.List.of(
                                new LotQuantityConsumption(
                                        stockLotId,
                                        original.version(),
                                        original.ownerCharacterId(),
                                        original.location(),
                                        3)));
        JdbcCharacterExpeditionStateRepository repository =
                new JdbcCharacterExpeditionStateRepository(dataSource);

        CharacterFlaskPreparationCommitExecution first =
                success(repository.commitFlaskPreparation(request, commit));
        CharacterFlaskPreparationCommitExecution replay =
                success(repository.commitFlaskPreparation(request, commit));

        assertEquals(1, first.record().version());
        assertEquals(3, first.infusionStockConsumed());
        assertFalse(first.transaction().replayed());
        assertTrue(replay.transaction().replayed());
        LotLocationRecord remaining = success(values.findLot(stockLotId)).orElseThrow();
        assertEquals(1, remaining.quantity());
        assertEquals(original.version() + 1, remaining.version());

        TransactionRequest staleRequest =
                TransactionRequest.forCharacter(
                        new TransactionId(UUID.randomUUID()),
                        "flask-rest-stale:" + UUID.randomUUID(),
                        characterId,
                        sessionId,
                        JdbcCharacterExpeditionStateRepository.CHARACTER_FLASK_PREPARATION_COMMIT,
                        "{\"stockConsumed\":1}",
                        "{\"schemaVersion\":2,\"flask\":{\"charges\":4}}",
                        "test-content-v1");
        Result<CharacterFlaskPreparationCommitExecution, TransactionErrorCode> stale =
                repository.commitFlaskPreparation(
                        staleRequest,
                        new CharacterFlaskPreparationCommit(
                                characterId,
                                0,
                                staleRequest.intendedOutputsJson(),
                                infusionStock,
                                java.util.List.of(
                                        new LotQuantityConsumption(
                                                stockLotId,
                                                remaining.version(),
                                                remaining.ownerCharacterId(),
                                                remaining.location(),
                                                1))));

        assertFalse(stale.isSuccess());
        assertEquals(
                TransactionErrorCode.VALUE_STALE_VERSION,
                ((Result.Failure<CharacterFlaskPreparationCommitExecution, TransactionErrorCode>)
                                stale)
                        .error());
        LotLocationRecord afterRollback = success(values.findLot(stockLotId)).orElseThrow();
        assertEquals(1, afterRollback.quantity());
        assertEquals(remaining.version(), afterRollback.version());
    }

    private static TransactionRequest request(
            UUID operationId,
            CharacterId characterId,
            SessionId sessionId,
            long expectedVersion,
            String payload) {
        return TransactionRequest.forCharacter(
                new TransactionId(operationId),
                "expedition-test:" + operationId,
                characterId,
                sessionId,
                JdbcCharacterExpeditionStateRepository.CHARACTER_EXPEDITION_STATE_COMMIT,
                "{\"expectedVersion\":" + expectedVersion + "}",
                payload,
                "test-content-v1");
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
}
