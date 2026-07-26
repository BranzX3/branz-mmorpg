package com.branz.mmorpg.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.player.PlayerProfileComponent;
import com.branz.mmorpg.api.player.PlayerSessionSnapshot;
import com.branz.mmorpg.api.player.PlayerSessionState;
import com.branz.mmorpg.storage.DatabaseConfig;
import com.branz.mmorpg.storage.DatabaseManager;
import com.branz.mmorpg.storage.player.FilePlayerProfileRecoveryStore;
import com.branz.mmorpg.storage.player.MySqlPlayerProfileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfEnvironmentVariable(named = "BRANZ_MYSQL_INTEGRATION", matches = "true")
class PlayerSessionMySqlIntegrationTest {
    @TempDir
    Path recoveryDirectory;

    private DatabaseManager database;
    private ExecutorService executor;

    @AfterEach
    void closeResources() throws InterruptedException {
        if (database != null) {
            database.close();
            database = null;
        }
        if (executor != null) {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            executor = null;
        }
    }

    @Test
    void databaseOutageJournalsAndReplaysTheDirtyProfileAfterReconnect() {
        UUID playerId = UUID.randomUUID();
        executor = Executors.newFixedThreadPool(
                4, Thread.ofPlatform().name("mysql-session-it-", 0).daemon(true).factory());
        database = DatabaseManager.connect(config());
        FilePlayerProfileRecoveryStore recovery =
                new FilePlayerProfileRecoveryStore(recoveryDirectory, executor);
        PlayerSessionManager firstRuntime = new PlayerSessionManager(
                new MySqlPlayerProfileStore(database, executor),
                Clock.systemUTC(),
                new PlayerSessionSavePolicy(1),
                recovery);
        PlayerSessionSnapshot active = firstRuntime.open(playerId, "Recovery", 7)
                .toCompletableFuture().join();
        firstRuntime.updateProfile(
                playerId,
                active.token(),
                PlayerProfileComponent.SETTINGS,
                profile -> profile.withSetting("language", "th_TH"));

        database.close();
        database = null;
        PlayerSessionSnapshot pending = firstRuntime.close(playerId, active.token())
                .toCompletableFuture().join();
        Path journal = recoveryDirectory.resolve(playerId + ".json");

        assertEquals(PlayerSessionState.SAVE_RETRY_PENDING, pending.state());
        assertTrue(Files.isRegularFile(journal));

        database = DatabaseManager.connect(config());
        PlayerSessionManager restartedRuntime = new PlayerSessionManager(
                new MySqlPlayerProfileStore(database, executor),
                Clock.systemUTC(),
                new PlayerSessionSavePolicy(1),
                recovery);
        PlayerSessionSnapshot recovered = restartedRuntime.open(playerId, "Recovery", 8)
                .toCompletableFuture().join();

        assertEquals(PlayerSessionState.ACTIVE, recovered.state());
        assertEquals("th_TH", recovered.profile().orElseThrow().settings().get("language"));
        assertEquals(1, recovered.profile().orElseThrow().revision());
        assertFalse(Files.exists(journal));

        PlayerSessionSnapshot closed = restartedRuntime.close(playerId, recovered.token())
                .toCompletableFuture().join();
        assertEquals(PlayerSessionState.CLOSED, closed.state());
    }

    private static DatabaseConfig config() {
        return new DatabaseConfig(
                environment("BRANZ_MYSQL_HOST", "127.0.0.1"),
                Integer.parseInt(environment("BRANZ_MYSQL_PORT", "3407")),
                environment("BRANZ_MYSQL_DATABASE", "branz_mmorpg_test"),
                environment("BRANZ_MYSQL_USERNAME", "root"),
                environment("BRANZ_MYSQL_PASSWORD", ""),
                6,
                5000);
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
