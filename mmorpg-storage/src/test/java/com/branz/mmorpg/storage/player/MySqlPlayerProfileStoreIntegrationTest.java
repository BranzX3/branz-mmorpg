package com.branz.mmorpg.storage.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.storage.DatabaseConfig;
import com.branz.mmorpg.storage.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "BRANZ_MYSQL_INTEGRATION", matches = "true")
class MySqlPlayerProfileStoreIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-07-26T10:00:00.123456Z");
    private static final Instant LATER = Instant.parse("2026-07-26T11:00:00.654321Z");

    private DatabaseManager database;
    private ExecutorService executor;
    private MySqlPlayerProfileStore store;

    @BeforeAll
    void startRepository() {
        DatabaseConfig config = new DatabaseConfig(
                environment("BRANZ_MYSQL_HOST", "127.0.0.1"),
                Integer.parseInt(environment("BRANZ_MYSQL_PORT", "3407")),
                environment("BRANZ_MYSQL_DATABASE", "branz_mmorpg_test"),
                environment("BRANZ_MYSQL_USERNAME", "root"),
                environment("BRANZ_MYSQL_PASSWORD", ""),
                6,
                5000);
        database = DatabaseManager.connect(config);
        executor = Executors.newFixedThreadPool(
                4, Thread.ofPlatform().name("mysql-profile-it-", 0).daemon(true).factory());
        store = new MySqlPlayerProfileStore(database, executor);
    }

    @AfterAll
    void stopRepository() throws InterruptedException {
        if (executor != null) {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        if (database != null) {
            database.close();
        }
    }

    @Test
    void flywayCreatesTheExpectedPlayerProfileSchema() throws SQLException {
        try (Connection connection = database.dataSource().getConnection()) {
            assertEquals(1, count(connection, """
                    SELECT COUNT(*)
                    FROM mmorpg_schema_history
                    WHERE version = '2' AND success = TRUE
                    """));
            assertEquals(1, count(connection, """
                    SELECT COUNT(*)
                    FROM mmorpg_schema_history
                    WHERE version = '2.1' AND success = TRUE
                    """));
            assertEquals("binary", columnType(connection, "player_uuid"));
            assertEquals("json", columnType(connection, "settings_json"));
            assertEquals("bigint", columnType(connection, "revision"));
        }
    }

    @Test
    void createsAndRoundTripsACompleteProfile() {
        UUID playerId = UUID.randomUUID();
        PlayerProfile created = store.loadOrCreate(playerId, "FirstName", CREATED_AT)
                .toCompletableFuture().join();
        PlayerProfile changed = new PlayerProfile(
                created.playerId(),
                created.lastKnownName(),
                created.schemaVersion(),
                created.createdAt(),
                LATER,
                Optional.of(ContentId.parse("branz:warrior")),
                Optional.of(ContentId.parse("branz:warrior/starter")),
                Optional.of(ContentId.parse("branz:spawn/tutorial")),
                Map.of("hud", "compact", "language", "th_TH"),
                created.revision());

        PlayerProfile saved = store.save(changed).toCompletableFuture().join();
        PlayerProfile reloaded = store.loadOrCreate(playerId, "Renamed", LATER)
                .toCompletableFuture().join();

        assertEquals(0, created.revision());
        assertEquals(1, saved.revision());
        assertEquals("Renamed", reloaded.lastKnownName());
        assertEquals(ContentId.parse("branz:warrior"), reloaded.classId().orElseThrow());
        assertEquals(ContentId.parse("branz:warrior/starter"), reloaded.selectedLoadoutId().orElseThrow());
        assertEquals(ContentId.parse("branz:spawn/tutorial"), reloaded.respawnPointId().orElseThrow());
        assertEquals(changed.settings(), reloaded.settings());
        assertEquals(1, reloaded.revision());
    }

    @Test
    void staleRevisionFailsWithAnOptimisticConflict() {
        UUID playerId = UUID.randomUUID();
        PlayerProfile loaded = store.loadOrCreate(playerId, "Conflict", CREATED_AT)
                .toCompletableFuture().join();
        PlayerProfile firstWriter = loaded.withSetting("writer", "first");
        PlayerProfile staleWriter = loaded.withSetting("writer", "stale");

        store.save(firstWriter).toCompletableFuture().join();
        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> store.save(staleWriter).toCompletableFuture().join());

        assertInstanceOf(PlayerProfileConflictException.class, rootCause(failure));
    }

    @Test
    void concurrentLoadOrCreateProducesExactlyOneProfileRow() throws SQLException {
        UUID playerId = UUID.randomUUID();
        List<CompletableFuture<PlayerProfile>> loads = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            loads.add(store.loadOrCreate(playerId, "Concurrent", CREATED_AT)
                    .toCompletableFuture());
        }

        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).join();

        assertTrue(loads.stream().map(CompletableFuture::join)
                .allMatch(profile -> profile.playerId().equals(playerId)));
        try (Connection connection = database.dataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT COUNT(*)
                        FROM mmorpg_player_profiles
                        WHERE player_uuid = UUID_TO_BIN(?)
                        """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }
    }

    private String columnType(Connection connection, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT DATA_TYPE
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'mmorpg_player_profiles'
                  AND column_name = ?
                """)) {
            statement.setString(1, column);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "missing column " + column);
                return result.getString(1);
            }
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
