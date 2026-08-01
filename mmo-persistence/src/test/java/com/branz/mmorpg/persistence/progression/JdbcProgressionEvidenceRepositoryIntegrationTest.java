package com.branz.mmorpg.persistence.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.migration.ClasspathMigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationErrorCode;
import com.branz.mmorpg.persistence.migration.PostgresMigrationRunner;
import com.branz.mmorpg.progression.evidence.EncounterOutcome;
import com.branz.mmorpg.progression.evidence.EvidenceCandidate;
import com.branz.mmorpg.progression.evidence.EvidenceSuppressionReason;
import com.branz.mmorpg.progression.evidence.EvidenceTargetKind;
import com.branz.mmorpg.progression.evidence.ProgressionTrack;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcProgressionEvidenceRepositoryIntegrationTest {
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
    void batchesSequentialEvidenceReplaysExactlyOnceAndSurvivesRestart() throws Exception {
        CharacterId characterId = new CharacterId(UUID.randomUUID());
        EvidenceCandidate first = candidate(characterId, UUID.randomUUID(), "same-pattern");
        EvidenceCandidate second = candidate(characterId, UUID.randomUUID(), "same-pattern");
        JdbcProgressionEvidenceRepository repository =
                new JdbcProgressionEvidenceRepository(dataSource);

        List<ProgressionEvidenceExecution> recorded =
                success(repository.recordBatch(List.of(first, second)));

        assertEquals(2, recorded.size());
        assertFalse(recorded.getFirst().replayed());
        assertFalse(recorded.getLast().replayed());
        assertEquals(
                recorded.getFirst().evidence().decision().awardedEvidence() * 0.60,
                recorded.getLast().evidence().decision().awardedEvidence(),
                0.00001);
        ProgressionTrackRecord afterBatch = recorded.getLast().track().orElseThrow();
        assertEquals(2, afterBatch.version());

        List<ProgressionEvidenceExecution> replayed =
                success(repository.recordBatch(List.of(first, second)));
        assertTrue(replayed.stream().allMatch(ProgressionEvidenceExecution::replayed));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM combat_progression_evidence"));

        JdbcProgressionEvidenceRepository restarted =
                new JdbcProgressionEvidenceRepository(dataSource);
        ProgressionTrackRecord restored = success(restarted.findTracks(characterId)).getFirst();
        assertEquals(afterBatch.evidence(), restored.evidence(), 0.00001);
        assertEquals(2, restored.version());
    }

    @Test
    void persistsSuppressionWithoutCreatingOrAdvancingTrack() throws Exception {
        CharacterId characterId = new CharacterId(UUID.randomUUID());
        EvidenceCandidate invulnerable =
                candidate(
                        characterId,
                        UUID.randomUUID(),
                        "invulnerable",
                        EvidenceTargetKind.INVULNERABLE_TARGET);
        JdbcProgressionEvidenceRepository repository =
                new JdbcProgressionEvidenceRepository(dataSource);

        ProgressionEvidenceExecution execution =
                success(repository.recordBatch(List.of(invulnerable))).getFirst();

        assertFalse(execution.evidence().decision().accepted());
        assertEquals(
                EvidenceSuppressionReason.INVULNERABLE_TARGET,
                execution.evidence().decision().suppressionReason());
        assertTrue(execution.track().isEmpty());
        assertTrue(success(repository.findTracks(characterId)).isEmpty());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM combat_progression_evidence"));
    }

    @Test
    void dailyCurveIsIndependentPerProgressionTrack() throws Exception {
        CharacterId characterId = new CharacterId(UUID.randomUUID());
        JdbcProgressionEvidenceRepository repository =
                new JdbcProgressionEvidenceRepository(dataSource);
        for (int index = 0; index < 7; index++) {
            success(
                    repository.recordBatch(
                            List.of(
                                    candidate(
                                            characterId,
                                            UUID.randomUUID(),
                                            "greatsword-pattern-" + index))));
        }
        EvidenceCandidate staff =
                candidate(
                        characterId,
                        UUID.randomUUID(),
                        "staff-pattern",
                        ProgressionTrack.mastery("staff"));

        ProgressionEvidenceExecution execution =
                success(repository.recordBatch(List.of(staff))).getFirst();

        assertEquals(1.0, execution.evidence().decision().factors().dailyCurve());
    }

    @Test
    void conflictingEvidenceIdentityRollsBackWholeBatch() throws Exception {
        CharacterId characterId = new CharacterId(UUID.randomUUID());
        UUID fixedId = UUID.randomUUID();
        EvidenceCandidate original = candidate(characterId, fixedId, "original");
        JdbcProgressionEvidenceRepository repository =
                new JdbcProgressionEvidenceRepository(dataSource);
        success(repository.recordBatch(List.of(original)));
        EvidenceCandidate beforeConflict = candidate(characterId, UUID.randomUUID(), "new");
        EvidenceCandidate conflicting = candidate(characterId, fixedId, "changed");

        Result<List<ProgressionEvidenceExecution>, ProgressionPersistenceErrorCode> result =
                repository.recordBatch(List.of(beforeConflict, conflicting));

        assertFalse(result.isSuccess());
        assertEquals(
                ProgressionPersistenceErrorCode.PROGRESSION_EVIDENCE_ID_CONFLICT,
                ((Result.Failure<
                                        List<ProgressionEvidenceExecution>,
                                        ProgressionPersistenceErrorCode>)
                                result)
                        .error());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM combat_progression_evidence"));
        assertEquals(1, success(repository.findTracks(characterId)).getFirst().version());
    }

    @Test
    void rejectsMixedCharacterBatchBeforeDatabaseMutation() throws Exception {
        EvidenceCandidate first =
                candidate(new CharacterId(UUID.randomUUID()), UUID.randomUUID(), "first");
        EvidenceCandidate second =
                candidate(new CharacterId(UUID.randomUUID()), UUID.randomUUID(), "second");
        JdbcProgressionEvidenceRepository repository =
                new JdbcProgressionEvidenceRepository(dataSource);

        Result<List<ProgressionEvidenceExecution>, ProgressionPersistenceErrorCode> result =
                repository.recordBatch(List.of(first, second));

        assertFalse(result.isSuccess());
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM combat_progression_evidence"));
    }

    private static EvidenceCandidate candidate(
            CharacterId characterId, UUID evidenceId, String fingerprint) {
        return candidate(
                characterId,
                evidenceId,
                fingerprint,
                EvidenceTargetKind.MEANINGFUL_ENCOUNTER,
                ProgressionTrack.mastery("greatsword"));
    }

    private static EvidenceCandidate candidate(
            CharacterId characterId,
            UUID evidenceId,
            String fingerprint,
            EvidenceTargetKind targetKind) {
        return new EvidenceCandidate(
                evidenceId,
                characterId,
                new EncounterId(UUID.randomUUID()),
                ProgressionTrack.mastery("greatsword"),
                fingerprint,
                "test-content-v1",
                targetKind,
                EncounterOutcome.VICTORY,
                10.0,
                100.0,
                100.0,
                0.8,
                0.8,
                0.8);
    }

    private static EvidenceCandidate candidate(
            CharacterId characterId, UUID evidenceId, String fingerprint, ProgressionTrack track) {
        return candidate(
                characterId,
                evidenceId,
                fingerprint,
                EvidenceTargetKind.MEANINGFUL_ENCOUNTER,
                track);
    }

    private static EvidenceCandidate candidate(
            CharacterId characterId,
            UUID evidenceId,
            String fingerprint,
            EvidenceTargetKind targetKind,
            ProgressionTrack track) {
        return new EvidenceCandidate(
                evidenceId,
                characterId,
                new EncounterId(UUID.randomUUID()),
                track,
                fingerprint,
                "test-content-v1",
                targetKind,
                EncounterOutcome.VICTORY,
                10.0,
                100.0,
                100.0,
                0.8,
                0.8,
                0.8);
    }

    private int scalarInt(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(sql)) {
            row.next();
            return row.getInt(1);
        }
    }

    private static <T> T success(Result<T, ProgressionPersistenceErrorCode> result) {
        assertTrue(
                result.isSuccess(),
                () ->
                        result instanceof Result.Failure<T, ProgressionPersistenceErrorCode> failure
                                ? failure.error().code() + ": " + failure.detail()
                                : "");
        return ((Result.Success<T, ProgressionPersistenceErrorCode>) result).value();
    }
}
