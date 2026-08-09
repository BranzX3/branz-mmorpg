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
class JdbcCharacterOnboardingStateRepositoryIntegrationTest {
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
    void choiceIsImmutableReplayableAndKitCompletionSurvivesRestart() throws Exception {
        CharacterId characterId = new CharacterId(UUID.randomUUID());
        SessionId sessionId = new SessionId(UUID.randomUUID());
        JdbcCharacterOnboardingStateRepository repository =
                new JdbcCharacterOnboardingStateRepository(dataSource);
        assertTrue(success(repository.find(characterId)).isEmpty());

        UUID chooseId = UUID.randomUUID();
        TransactionRequest choose =
                request(
                        chooseId,
                        characterId,
                        sessionId,
                        JdbcCharacterOnboardingStateRepository.FOUNDATION_CHOOSE,
                        "{\"foundation\":\"GREATSWORD\"}");
        CharacterOnboardingStateCommitExecution chosen =
                success(repository.chooseFoundation(choose, characterId, "GREATSWORD"));
        assertEquals("GREATSWORD", chosen.record().foundationId());
        assertEquals(1, chosen.record().version());
        assertFalse(chosen.record().kitReady());
        assertFalse(chosen.transaction().replayed());

        CharacterOnboardingStateCommitExecution replayed =
                success(repository.chooseFoundation(choose, characterId, "GREATSWORD"));
        assertTrue(replayed.transaction().replayed());
        assertEquals("GREATSWORD", replayed.record().foundationId());

        Result<CharacterOnboardingStateCommitExecution, TransactionErrorCode> conflicting =
                repository.chooseFoundation(
                        request(
                                UUID.randomUUID(),
                                characterId,
                                sessionId,
                                JdbcCharacterOnboardingStateRepository.FOUNDATION_CHOOSE,
                                "{\"foundation\":\"BOW\"}"),
                        characterId,
                        "BOW");
        assertFalse(conflicting.isSuccess());
        assertEquals(
                TransactionErrorCode.VALUE_STALE_VERSION,
                ((Result.Failure<CharacterOnboardingStateCommitExecution, TransactionErrorCode>)
                                conflicting)
                        .error());

        UUID readyId = UUID.randomUUID();
        TransactionRequest ready =
                request(
                        readyId,
                        characterId,
                        sessionId,
                        JdbcCharacterOnboardingStateRepository.KIT_READY,
                        "{\"kitReady\":true}");
        CharacterOnboardingStateCommitExecution completed =
                success(repository.markKitReady(ready, characterId, 1));
        assertTrue(completed.record().kitReady());
        assertEquals(2, completed.record().version());
        assertFalse(completed.transaction().replayed());

        CharacterOnboardingStateCommitExecution readyReplay =
                success(repository.markKitReady(ready, characterId, 1));
        assertTrue(readyReplay.transaction().replayed());
        assertTrue(readyReplay.record().kitReady());

        JdbcCharacterOnboardingStateRepository restarted =
                new JdbcCharacterOnboardingStateRepository(dataSource);
        CharacterOnboardingStateRecord restored =
                success(restarted.find(characterId)).orElseThrow();
        assertEquals("GREATSWORD", restored.foundationId());
        assertTrue(restored.kitReady());
        assertEquals(2, restored.version());
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM audit_log"));
    }

    private static TransactionRequest request(
            UUID operationId,
            CharacterId characterId,
            SessionId sessionId,
            String operation,
            String outputs) {
        return TransactionRequest.forCharacter(
                new TransactionId(operationId),
                "onboarding-test:" + operationId,
                characterId,
                sessionId,
                operation,
                "{\"expected\":\"current\"}",
                outputs,
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
