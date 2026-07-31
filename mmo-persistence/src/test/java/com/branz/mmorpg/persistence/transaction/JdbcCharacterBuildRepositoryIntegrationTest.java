package com.branz.mmorpg.persistence.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
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
class JdbcCharacterBuildRepositoryIntegrationTest {
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
    void commitsReplaysUpdatesAndReloadsBuildWithOptimisticVersion() throws Exception {
        CharacterId characterId = new CharacterId(UUID.randomUUID());
        SessionId sessionId = new SessionId(UUID.randomUUID());
        JdbcCharacterBuildRepository repository = new JdbcCharacterBuildRepository(dataSource);
        assertTrue(success(repository.find(characterId)).isEmpty());

        UUID firstId = UUID.randomUUID();
        String firstPayload =
                "{\"schemaVersion\":1,\"attunementCapacity\":6,\"techniques\":{},"
                        + "\"form\":null,\"attunedEffects\":[]}";
        TransactionRequest firstRequest = request(firstId, characterId, sessionId, 0, firstPayload);
        CharacterBuildCommitExecution first =
                success(
                        repository.commit(
                                firstRequest,
                                new CharacterBuildCommit(characterId, 0, firstPayload)));
        assertEquals(1, first.record().version());
        assertFalse(first.transaction().replayed());

        CharacterBuildCommitExecution replay =
                success(
                        repository.commit(
                                firstRequest,
                                new CharacterBuildCommit(characterId, 0, firstPayload)));
        assertTrue(replay.transaction().replayed());
        assertEquals(1, replay.record().version());

        String secondPayload =
                "{\"schemaVersion\":1,\"attunementCapacity\":6,\"techniques\":{},"
                        + "\"form\":null,\"attunedEffects\":[\"spell.ember.fire_lance\"]}";
        UUID secondId = UUID.randomUUID();
        CharacterBuildCommitExecution second =
                success(
                        repository.commit(
                                request(secondId, characterId, sessionId, 1, secondPayload),
                                new CharacterBuildCommit(characterId, 1, secondPayload)));
        assertEquals(2, second.record().version());

        JdbcCharacterBuildRepository restarted = new JdbcCharacterBuildRepository(dataSource);
        CharacterBuildRecord restored = success(restarted.find(characterId)).orElseThrow();
        assertEquals(2, restored.version());
        assertTrue(restored.payloadJson().contains("spell.ember.fire_lance"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM audit_log"));

        Result<CharacterBuildCommitExecution, TransactionErrorCode> stale =
                restarted.commit(
                        request(UUID.randomUUID(), characterId, sessionId, 1, firstPayload),
                        new CharacterBuildCommit(characterId, 1, firstPayload));
        assertFalse(stale.isSuccess());
        assertEquals(
                TransactionErrorCode.VALUE_STALE_VERSION,
                ((Result.Failure<CharacterBuildCommitExecution, TransactionErrorCode>) stale)
                        .error());
    }

    private static TransactionRequest request(
            UUID operationId,
            CharacterId characterId,
            SessionId sessionId,
            long expectedVersion,
            String payload) {
        return TransactionRequest.forCharacter(
                new TransactionId(operationId),
                "build-test:" + operationId,
                characterId,
                sessionId,
                JdbcCharacterBuildRepository.CHARACTER_BUILD_COMMIT,
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
