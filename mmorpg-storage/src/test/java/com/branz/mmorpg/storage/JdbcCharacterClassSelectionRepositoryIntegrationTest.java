package com.branz.mmorpg.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.CharacterClassId;
import com.branz.mmorpg.api.character.CharacterClassRole;
import com.branz.mmorpg.api.character.StarterGrantPlan;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.skill.ResourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "BRANZ_MYSQL_INTEGRATION", matches = "true")
class JdbcCharacterClassSelectionRepositoryIntegrationTest {
    private DatabaseManager database;
    private JdbcPlayerProfileRepository profiles;
    private JdbcCharacterClassSelectionRepository selections;

    @BeforeEach void connect() {
        database = DatabaseManager.connect(new DatabaseConfig(
                environment("BRANZ_MYSQL_HOST", "127.0.0.1"),
                Integer.parseInt(environment("BRANZ_MYSQL_PORT", "3407")),
                environment("BRANZ_MYSQL_DATABASE", "branz_mmorpg_test"),
                environment("BRANZ_MYSQL_USERNAME", "root"),
                environment("BRANZ_MYSQL_PASSWORD", ""), 4, 5000));
        profiles = new JdbcPlayerProfileRepository(database);
        selections = new JdbcCharacterClassSelectionRepository(database);
    }

    @AfterEach void close() { if (database != null) database.close(); }

    @Test
    void selectionAndStarterPlanCommitExactlyOnce() {
        UUID player = UUID.randomUUID();
        long revision = profiles.loadOrCreate(player, "I3Test").revision();
        OperationId operation = OperationId.of("class", "selection", player, "integration");

        var first = selections.select(player, revision, operation, warrior(), 9,
                Instant.parse("2026-07-26T10:00:00Z"));
        var retry = selections.select(player, revision, operation, warrior(), 9,
                Instant.parse("2026-07-26T10:00:01Z"));

        assertTrue(first.applied());
        assertFalse(retry.applied());
        assertEquals(first.snapshot(), retry.snapshot());
        assertEquals(first.starterGrantPlan(), retry.starterGrantPlan());
        assertEquals(CharacterClassId.WARRIOR.value(),
                profiles.loadOrCreate(player, "I3Test").classId().orElseThrow());

        MMOException permanent = assertThrows(MMOException.class, () -> selections.select(
                player, first.snapshot().profileRevision(),
                OperationId.of("class", "selection", player, "another"),
                warrior(), 9, Instant.now()));
        assertEquals(ErrorCode.INVALID_ARGUMENT, permanent.code());
    }

    private static CharacterClassDefinition warrior() {
        StarterGrantPlan starter = new StarterGrantPlan(ContentId.parse("branz:warrior_starter"),
                1, ContentId.parse("branz:broadsword"),
                List.of(ContentId.parse("branz:basic_strike")), Map.of());
        return new CharacterClassDefinition(CharacterClassId.WARRIOR.value(), "Warrior", 1,
                Set.of(CharacterClassRole.DAMAGE), Map.of("strength", 12.0),
                ResourceType.STAMINA, Set.of("sword"), Set.of("heavy"),
                List.of(ContentId.parse("branz:heavy_slash")),
                ContentId.parse("branz:heavy_slash"), ContentId.parse("branz:warrior_root"),
                starter, Set.of("physical"));
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
