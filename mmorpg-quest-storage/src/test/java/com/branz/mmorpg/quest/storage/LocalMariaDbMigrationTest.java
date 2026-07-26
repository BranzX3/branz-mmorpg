package com.branz.mmorpg.quest.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.quest.api.QuestProgress;
import com.branz.mmorpg.quest.api.QuestState;
import com.branz.mmorpg.storage.DatabaseConfig;
import com.branz.mmorpg.storage.DatabaseManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.sql.DriverManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Explicit local fallback when Docker is unavailable on a developer machine. */
final class LocalMariaDbMigrationTest {
    private static final String DATABASE = "branz_mmorpg_integration_test";
    private static boolean enabled;

    @BeforeAll
    static void createDatabase() throws Exception {
        enabled = Boolean.parseBoolean(System.getenv("BRANZ_LOCAL_DB_TEST"));
        Assumptions.assumeTrue(enabled);
        try (var connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:3306/mysql?useSSL=false"
                        + "&allowPublicKeyRetrieval=true", "root", "");
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        }
    }

    @AfterAll
    static void removeDatabase() throws Exception {
        if (!enabled || Boolean.parseBoolean(System.getenv("BRANZ_KEEP_TEST_DB"))) return;
        try (var connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:3306/mysql?useSSL=false"
                        + "&allowPublicKeyRetrieval=true", "root", "");
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
        }
    }

    @Test
    void migratesAndExercisesWorldRegistryRepository() throws Exception {
        DatabaseConfig config = new DatabaseConfig(
                "127.0.0.1", 3306, DATABASE, "root", "", 4, 5_000);
        try (DatabaseManager database = DatabaseManager.connect(config)) {
            JdbcQuestWorldStore store = new JdbcQuestWorldStore(database);
            var value = new JdbcQuestWorldStore.LocationRecord(
                    "test:marker", java.util.UUID.randomUUID(),
                    1.25, 64, -3.5, 90, 0);
            store.save(value);
            assertEquals(value, store.locations().getFirst());
            int migration = database.inTransaction(connection -> {
                try (var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM mmorpg_schema_history WHERE success = 1");
                     var row = statement.executeQuery()) {
                    row.next();
                    return row.getInt(1);
                }
            });
            assertEquals(17, migration);

            JdbcQuestProgressStore progressStore = new JdbcQuestProgressStore(database);
            UUID playerId = UUID.randomUUID();
            Instant now = Instant.parse("2026-07-26T00:00:00Z");
            QuestProgress first = progress(
                    playerId, ContentId.parse("test:first"), now);
            QuestProgress second = progress(
                    playerId, ContentId.parse("test:second"), now);
            progressStore.insert(first, List.of());
            progressStore.insert(second, List.of());

            UUID sharedEvent = UUID.randomUUID();
            assertTrue(progressStore.commit(
                    first, advance(first), sharedEvent, List.of()).applied());
            assertTrue(progressStore.commit(
                    second, advance(second), sharedEvent, List.of()).applied());
            assertFalse(progressStore.commit(
                    first, advance(first), sharedEvent, List.of()).applied());
        }
    }

    private static QuestProgress progress(
            UUID playerId, ContentId questId, Instant now) {
        return new QuestProgress(playerId, questId, 1, 0, QuestState.ACTIVE,
                "start", UUID.randomUUID(), Map.of(), Map.of(),
                now, now, Optional.empty());
    }

    private static QuestProgress advance(QuestProgress before) {
        return new QuestProgress(before.playerId(), before.questId(),
                before.definitionVersion(), before.revision() + 1,
                before.state(), before.stageId(), before.occurrenceId(),
                before.objectives(), before.flags(), before.startedAt(),
                before.updatedAt().plusSeconds(1), before.completedAt());
    }
}
