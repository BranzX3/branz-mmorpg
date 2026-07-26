package com.branz.mmorpg.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.character.CharacterClassProgress;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.mastery.MasterySnapshot;
import com.branz.mmorpg.api.operation.OperationId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "BRANZ_MYSQL_INTEGRATION", matches = "true")
class JdbcCombatProgressionRepositoryIntegrationTest {
    private DatabaseManager database;
    private JdbcCharacterClassProgressionRepository classes;
    private JdbcCombatMasteryRepository masteries;
    private UUID player;

    @BeforeEach void connect() {
        database = DatabaseManager.connect(new DatabaseConfig(
                environment("BRANZ_MYSQL_HOST", "127.0.0.1"),
                Integer.parseInt(environment("BRANZ_MYSQL_PORT", "3407")),
                environment("BRANZ_MYSQL_DATABASE", "branz_mmorpg_test"),
                environment("BRANZ_MYSQL_USERNAME", "root"),
                environment("BRANZ_MYSQL_PASSWORD", ""), 4, 5000));
        player = UUID.randomUUID();
        new JdbcPlayerProfileRepository(database).loadOrCreate(player, "I6Test");
        classes = new JdbcCharacterClassProgressionRepository(database);
        masteries = new JdbcCombatMasteryRepository(database);
    }

    @AfterEach void close() { if (database != null) database.close(); }

    @Test
    void classXpAndTreeRanksCommitExactlyOnce() {
        ContentId classId = ContentId.parse("branz:warrior");
        ContentId nodeId = ContentId.parse("branz:warrior_root");
        OperationId operation = OperationId.of("classxp", classId.value(), player, "i6-class");
        Instant now = Instant.parse("2026-07-26T10:00:00Z");

        var first = classes.mutate(player, classId, 1, operation, "integration",
                before -> new CharacterClassProgress(player, classId, 2, 100L, 0, 1,
                        Map.of(nodeId, 1), now));
        var replay = classes.mutate(player, classId, 1, operation, "integration",
                before -> { throw new AssertionError("duplicate mutation executed"); });

        assertTrue(first.applied());
        assertFalse(replay.applied());
        assertEquals(1, classes.load(player, classId, 1, now).rank(nodeId));
        assertEquals(100L, replay.after().totalXp());
    }

    @Test
    void masteryPointsAndTreeRanksRoundTripExactlyOnce() {
        ContentId masteryId = ContentId.parse("branz:broadsword_mastery");
        ContentId nodeId = ContentId.parse("branz:broadsword_guard");
        OperationId operation = OperationId.of(
                "mastery", masteryId.value(), player, "i6-mastery");
        Instant now = Instant.parse("2026-07-26T10:00:00Z");

        var first = masteries.mutate(player, masteryId, operation, 100L,
                before -> new MasterySnapshot(masteryId, 2, 100L, 0, 1,
                        Map.of(nodeId, 1), now));
        var replay = masteries.mutate(player, masteryId, operation, 100L,
                before -> { throw new AssertionError("duplicate mutation executed"); });

        assertTrue(first.applied());
        assertFalse(replay.applied());
        MasterySnapshot loaded = masteries.load(player).get(masteryId);
        assertEquals(1, loaded.rank(nodeId));
        assertEquals(100L, loaded.totalXp());
        assertEquals(0L, replay.awardedXp());
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
