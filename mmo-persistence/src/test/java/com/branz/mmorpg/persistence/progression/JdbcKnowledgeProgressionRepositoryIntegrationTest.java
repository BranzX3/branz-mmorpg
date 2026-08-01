package com.branz.mmorpg.persistence.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.migration.ClasspathMigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationCatalog;
import com.branz.mmorpg.persistence.migration.MigrationErrorCode;
import com.branz.mmorpg.persistence.migration.PostgresMigrationRunner;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import com.branz.mmorpg.progression.knowledge.KnowledgeType;
import com.branz.mmorpg.progression.renown.RenownDeedCandidate;
import com.branz.mmorpg.progression.renown.RenownSuppressionReason;
import com.branz.mmorpg.progression.teaching.TeachingCompletion;
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
class JdbcKnowledgeProgressionRepositoryIntegrationTest {
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
        MigrationCatalog catalog =
                ((Result.Success<MigrationCatalog, MigrationErrorCode>) loaded).value();
        assertTrue(new PostgresMigrationRunner(dataSource).migrate(catalog).isSuccess());
    }

    @Test
    void atomicallyCommitsKnowledgeTeacherRenownAndExactReplayAcrossRestart() throws Exception {
        CharacterId teacher = character();
        CharacterId student = character();
        TeachingCommitRequest request =
                request(teacher, student, UUID.randomUUID(), UUID.randomUUID());
        JdbcKnowledgeProgressionRepository repository =
                new JdbcKnowledgeProgressionRepository(dataSource);

        TeachingCommitExecution committed = success(repository.commitTeaching(request));

        assertFalse(committed.replayed());
        assertEquals(
                request.completion().learnedTechnique(), committed.learnedKnowledge().knowledge());
        assertEquals(20, committed.teacherDeed().decision().awardedRenown());
        assertEquals(20, committed.teacherRenown().orElseThrow().renown());
        assertEquals(1, committed.teacherRenown().orElseThrow().version());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM character_knowledge"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM teaching_completion_journal"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM renown_deed_journal"));

        TeachingCommitExecution replayed = success(repository.commitTeaching(request));
        assertTrue(replayed.replayed());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM character_knowledge"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM renown_deed_journal"));
        assertEquals(1, replayed.teacherRenown().orElseThrow().version());

        JdbcKnowledgeProgressionRepository restarted =
                new JdbcKnowledgeProgressionRepository(dataSource);
        assertEquals(1, success(restarted.findKnowledge(student)).size());
        assertEquals(20, success(restarted.findRenown(teacher)).orElseThrow().renown());
    }

    @Test
    void repeatedMentorshipDeedsDiminishAndFourthStillTeachesWithoutRenown() throws Exception {
        CharacterId teacher = character();
        JdbcKnowledgeProgressionRepository repository =
                new JdbcKnowledgeProgressionRepository(dataSource);
        TeachingCommitExecution first =
                success(
                        repository.commitTeaching(
                                request(
                                        teacher,
                                        character(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID())));
        TeachingCommitExecution second =
                success(
                        repository.commitTeaching(
                                request(
                                        teacher,
                                        character(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID())));
        TeachingCommitExecution third =
                success(
                        repository.commitTeaching(
                                request(
                                        teacher,
                                        character(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID())));
        TeachingCommitExecution fourth =
                success(
                        repository.commitTeaching(
                                request(
                                        teacher,
                                        character(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID())));

        assertEquals(20, first.teacherDeed().decision().awardedRenown());
        assertEquals(10, second.teacherDeed().decision().awardedRenown());
        assertEquals(5, third.teacherDeed().decision().awardedRenown());
        assertEquals(0, fourth.teacherDeed().decision().awardedRenown());
        assertEquals(
                RenownSuppressionReason.DAILY_REPETITION_EXHAUSTED,
                fourth.teacherDeed().decision().suppressionReason());
        assertEquals(35, fourth.teacherRenown().orElseThrow().renown());
        assertEquals(3, fourth.teacherRenown().orElseThrow().version());
        assertEquals(4, scalarInt("SELECT COUNT(*) FROM character_knowledge"));
        assertEquals(4, scalarInt("SELECT COUNT(*) FROM teaching_completion_journal"));
    }

    @Test
    void mismatchedSessionReplayRollsBackEveryNewOutput() throws Exception {
        CharacterId teacher = character();
        UUID sessionId = UUID.randomUUID();
        JdbcKnowledgeProgressionRepository repository =
                new JdbcKnowledgeProgressionRepository(dataSource);
        TeachingCommitRequest original =
                request(teacher, character(), sessionId, UUID.randomUUID());
        success(repository.commitTeaching(original));
        TeachingCommitRequest conflict =
                request(teacher, character(), sessionId, UUID.randomUUID());

        Result<TeachingCommitExecution, KnowledgePersistenceErrorCode> result =
                repository.commitTeaching(conflict);

        assertFailure(result, KnowledgePersistenceErrorCode.TEACHING_SESSION_ID_CONFLICT);
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM character_knowledge"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM renown_deed_journal"));
        assertEquals(20, success(repository.findRenown(teacher)).orElseThrow().renown());
    }

    @Test
    void deedReuseAndAlreadyLearnedTechniqueCannotPartiallyRewardTeacher() throws Exception {
        CharacterId teacher = character();
        CharacterId firstStudent = character();
        UUID deedId = UUID.randomUUID();
        JdbcKnowledgeProgressionRepository repository =
                new JdbcKnowledgeProgressionRepository(dataSource);
        success(
                repository.commitTeaching(
                        request(teacher, firstStudent, UUID.randomUUID(), deedId)));

        Result<TeachingCommitExecution, KnowledgePersistenceErrorCode> deedConflict =
                repository.commitTeaching(request(teacher, character(), UUID.randomUUID(), deedId));
        Result<TeachingCommitExecution, KnowledgePersistenceErrorCode> alreadyLearned =
                repository.commitTeaching(
                        request(teacher, firstStudent, UUID.randomUUID(), UUID.randomUUID()));

        assertFailure(deedConflict, KnowledgePersistenceErrorCode.RENOWN_DEED_ID_CONFLICT);
        assertFailure(alreadyLearned, KnowledgePersistenceErrorCode.KNOWLEDGE_ALREADY_LEARNED);
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM character_knowledge"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM renown_deed_journal"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM teaching_completion_journal"));
        assertEquals(20, success(repository.findRenown(teacher)).orElseThrow().renown());
    }

    private static TeachingCommitRequest request(
            CharacterId teacher, CharacterId student, UUID sessionId, UUID deedId) {
        KnowledgeKey technique =
                new KnowledgeKey(
                        KnowledgeType.TECHNIQUE,
                        DefinitionId.of("technique.greatsword.cleaving_arc"));
        TeachingCompletion completion =
                new TeachingCompletion(sessionId, teacher, student, technique);
        RenownDeedCandidate deed =
                new RenownDeedCandidate(
                        deedId,
                        teacher,
                        DefinitionId.of("renown.mentorship"),
                        "mentorship:greatsword:utc-day",
                        20,
                        "test-content-v1");
        return new TeachingCommitRequest(completion, deed);
    }

    private int scalarInt(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(sql)) {
            row.next();
            return row.getInt(1);
        }
    }

    private static CharacterId character() {
        return new CharacterId(UUID.randomUUID());
    }

    private static <T> T success(Result<T, KnowledgePersistenceErrorCode> result) {
        assertTrue(
                result.isSuccess(),
                () ->
                        result instanceof Result.Failure<T, KnowledgePersistenceErrorCode> failure
                                ? failure.error().code() + ": " + failure.detail()
                                : "");
        return ((Result.Success<T, KnowledgePersistenceErrorCode>) result).value();
    }

    private static void assertFailure(
            Result<?, KnowledgePersistenceErrorCode> result,
            KnowledgePersistenceErrorCode expected) {
        assertFalse(result.isSuccess());
        assertEquals(expected, ((Result.Failure<?, KnowledgePersistenceErrorCode>) result).error());
    }
}
