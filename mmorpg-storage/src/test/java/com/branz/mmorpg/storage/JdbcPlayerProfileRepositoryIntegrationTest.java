package com.branz.mmorpg.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.lifeskill.LifeSkillProfile;
import com.branz.mmorpg.api.lifeskill.LifeSkillProgress;
import com.branz.mmorpg.api.lifeskill.LifeSkillSnapshot;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.player.PlayerProfile;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "BRANZ_MYSQL_INTEGRATION", matches = "true")
class JdbcPlayerProfileRepositoryIntegrationTest {
    private DatabaseManager database;
    private JdbcPlayerProfileRepository repository;

    @BeforeEach
    void connect() {
        database = DatabaseManager.connect(new DatabaseConfig(
                environment("BRANZ_MYSQL_HOST", "127.0.0.1"),
                Integer.parseInt(environment("BRANZ_MYSQL_PORT", "3407")),
                environment("BRANZ_MYSQL_DATABASE", "branz_mmorpg_test"),
                environment("BRANZ_MYSQL_USERNAME", "root"),
                environment("BRANZ_MYSQL_PASSWORD", ""),
                4,
                5000));
        repository = new JdbcPlayerProfileRepository(database);
    }

    @AfterEach
    void close() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void preservesClassRevisionSettingsAndLifeSkillsInOneSessionStore() {
        UUID playerId = UUID.randomUUID();
        ContentId warrior = ContentId.parse("branz:warrior");
        ContentId mining = ContentId.parse("branz:mining");
        ContentId stoneworker = ContentId.parse("branz:mining_stoneworker");
        Instant now = Instant.parse("2026-07-26T12:00:00Z");

        PlayerProfile created = repository.loadOrCreate(playerId, "MergeTest");
        new JdbcCharacterClassSelectionRepository(database).select(
                playerId, created.revision(),
                OperationId.of("class", "selection", playerId, "profile-integration"),
                JdbcCharacterClassSelectionRepositoryIntegrationTest.warrior(), 9, now);
        PlayerProfile selected = repository.loadOrCreate(playerId, "MergeTest");
        PlayerProfile changed = new PlayerProfile(
                playerId,
                "MergeTest",
                selected.schemaVersion(),
                selected.createdAt(),
                now,
                selected.classId(),
                Optional.of(ContentId.parse("branz:warrior/starter")),
                Optional.empty(),
                Map.of("language", "th_TH"),
                selected.revision());
        repository.saveProfile(changed);

        PlayerProfile saved = repository.loadOrCreate(playerId, "MergeTest");
        assertEquals(2, saved.revision());
        assertEquals(warrior, saved.classId().orElseThrow());
        assertEquals("th_TH", saved.setting("language", "missing"));

        LifeSkillProgress progress = new LifeSkillProgress(mining, 3, 412, 1, 4, now);
        LifeSkillProfile lifeSkills = new LifeSkillProfile(
                playerId,
                Map.of(mining, new LifeSkillSnapshot(progress, Map.of(stoneworker, 1))),
                now);
        repository.saveSession(saved.withSetting("hud", "compact"), lifeSkills);

        PlayerProfile sessionProfile = repository.loadOrCreate(playerId, "MergeTest");
        assertEquals(3, sessionProfile.revision());
        assertEquals("compact", sessionProfile.setting("hud", "missing"));
        assertEquals(412, repository.loadLifeSkills(playerId).skill(mining).totalXp());
    }

    @Test
    void rejectsAStaleProfileRevision() {
        UUID playerId = UUID.randomUUID();
        PlayerProfile loaded = repository.loadOrCreate(playerId, "Conflict");
        repository.saveProfile(loaded.withSetting("writer", "first"));

        MMOException conflict = assertThrows(
                MMOException.class,
                () -> repository.saveProfile(loaded.withSetting("writer", "stale")));

        assertEquals(ErrorCode.STORAGE_FAILURE, conflict.code());
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
