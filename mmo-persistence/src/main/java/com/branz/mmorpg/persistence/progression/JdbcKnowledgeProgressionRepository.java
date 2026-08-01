package com.branz.mmorpg.persistence.progression;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import com.branz.mmorpg.progression.knowledge.KnowledgeType;
import com.branz.mmorpg.progression.renown.RenownContext;
import com.branz.mmorpg.progression.renown.RenownDecision;
import com.branz.mmorpg.progression.renown.RenownDeedCandidate;
import com.branz.mmorpg.progression.renown.RenownEngine;
import com.branz.mmorpg.progression.renown.RenownSuppressionReason;
import com.branz.mmorpg.progression.teaching.TeachingCompletion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Atomic PostgreSQL authority for learned knowledge, teaching completion and Renown. */
public final class JdbcKnowledgeProgressionRepository implements KnowledgeProgressionRepository {
    private static final String KNOWLEDGE_COLUMNS =
            "character_id, knowledge_type, definition_id, source_type, source_id, "
                    + "content_version, learned_at";
    private static final String RENOWN_COLUMNS = "character_id, renown, version, updated_at";
    private static final String DEED_COLUMNS =
            "deed_id, character_id, deed_type, novelty_fingerprint, base_renown, "
                    + "content_version, accepted, awarded_renown, resulting_renown, "
                    + "repetition_factor, suppression_reason, recorded_at";
    private static final String TEACHING_COLUMNS =
            "teaching_session_id, teacher_id, student_id, knowledge_type, definition_id, "
                    + "deed_id, content_version, completed_at";

    private final DataSource dataSource;
    private final RenownEngine renownEngine;

    public JdbcKnowledgeProgressionRepository(DataSource dataSource) {
        this(dataSource, new RenownEngine());
    }

    public JdbcKnowledgeProgressionRepository(DataSource dataSource, RenownEngine renownEngine) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.renownEngine = Objects.requireNonNull(renownEngine, "renownEngine");
    }

    @Override
    public Result<List<KnowledgeRecord>, KnowledgePersistenceErrorCode> findKnowledge(
            CharacterId characterId) {
        Objects.requireNonNull(characterId, "characterId");
        try (Connection connection = dataSource.getConnection()) {
            return Result.success(findKnowledge(connection, characterId));
        } catch (SQLException | IllegalArgumentException exception) {
            return failure(exception);
        }
    }

    @Override
    public Result<Optional<RenownRecord>, KnowledgePersistenceErrorCode> findRenown(
            CharacterId characterId) {
        Objects.requireNonNull(characterId, "characterId");
        try (Connection connection = dataSource.getConnection()) {
            return Result.success(findRenown(connection, characterId));
        } catch (SQLException | IllegalArgumentException exception) {
            return failure(exception);
        }
    }

    @Override
    public Result<TeachingCommitExecution, KnowledgePersistenceErrorCode> commitTeaching(
            TeachingCommitRequest request) {
        Objects.requireNonNull(request, "request");
        TeachingCompletion completion = request.completion();
        if (completion.teacherId().equals(completion.studentId())) {
            return Result.failure(
                    KnowledgePersistenceErrorCode.TEACHING_REQUEST_INVALID,
                    "Teacher and student must be different characters.");
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                lockParticipants(connection, completion.teacherId(), completion.studentId());
                Result<TeachingCommitExecution, KnowledgePersistenceErrorCode> result =
                        commitLocked(connection, request);
                if (result.isSuccess()) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
                return result;
            } catch (SQLException | IllegalArgumentException exception) {
                rollbackQuietly(connection);
                return failure(exception);
            } finally {
                restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (SQLException exception) {
            return failure(exception);
        }
    }

    private Result<TeachingCommitExecution, KnowledgePersistenceErrorCode> commitLocked(
            Connection connection, TeachingCommitRequest request) throws SQLException {
        TeachingCompletion completion = request.completion();
        Optional<TeachingCompletionRecord> existing =
                findTeaching(connection, completion.teachingSessionId());
        if (existing.isPresent()) {
            return replay(connection, request, existing.orElseThrow());
        }
        if (findDeed(connection, request.teacherReward().deedId()).isPresent()) {
            return Result.failure(
                    KnowledgePersistenceErrorCode.RENOWN_DEED_ID_CONFLICT,
                    "Renown deed UUID is already bound to another durable operation.");
        }
        if (findKnowledge(connection, completion.studentId(), completion.learnedTechnique())
                .isPresent()) {
            return Result.failure(
                    KnowledgePersistenceErrorCode.KNOWLEDGE_ALREADY_LEARNED,
                    "Student already knows " + completion.learnedTechnique().id().value() + ".");
        }

        Optional<RenownRecord> currentRenown = findRenown(connection, completion.teacherId());
        RenownDecision decision =
                renownEngine.evaluate(
                        request.teacherReward(),
                        new RenownContext(
                                currentRenown.map(RenownRecord::renown).orElse(0L),
                                identicalDeedsToday(connection, request.teacherReward()),
                                false));
        KnowledgeRecord knowledge = insertKnowledge(connection, request);
        Optional<RenownRecord> resultingRenown =
                decision.accepted()
                        ? Optional.of(
                                mutateRenown(
                                        connection,
                                        completion.teacherId(),
                                        currentRenown,
                                        decision))
                        : currentRenown;
        RenownDeedRecord deed = insertDeed(connection, request.teacherReward(), decision);
        TeachingCompletionRecord teaching = insertTeaching(connection, request);
        return Result.success(
                new TeachingCommitExecution(teaching, knowledge, deed, resultingRenown, false));
    }

    private Result<TeachingCommitExecution, KnowledgePersistenceErrorCode> replay(
            Connection connection, TeachingCommitRequest request, TeachingCompletionRecord teaching)
            throws SQLException {
        RenownDeedRecord deed = findDeed(connection, teaching.deedId()).orElseThrow();
        boolean exact =
                teaching.completion().equals(request.completion())
                        && teaching.contentVersion()
                                .equals(request.teacherReward().contentVersion())
                        && deed.candidate().equals(request.teacherReward());
        if (!exact) {
            return Result.failure(
                    KnowledgePersistenceErrorCode.TEACHING_SESSION_ID_CONFLICT,
                    "Teaching session UUID is bound to different immutable input.");
        }
        KnowledgeRecord knowledge =
                findKnowledge(
                                connection,
                                request.completion().studentId(),
                                request.completion().learnedTechnique())
                        .orElseThrow();
        return Result.success(
                new TeachingCommitExecution(
                        teaching,
                        knowledge,
                        deed,
                        findRenown(connection, request.completion().teacherId()),
                        true));
    }

    private static KnowledgeRecord insertKnowledge(
            Connection connection, TeachingCommitRequest request) throws SQLException {
        TeachingCompletion completion = request.completion();
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO character_knowledge("
                                + KNOWLEDGE_COLUMNS
                                + ") VALUES (?, ?, ?, 'PLAYER_TEACHING', ?, ?, CURRENT_TIMESTAMP) "
                                + "RETURNING "
                                + KNOWLEDGE_COLUMNS)) {
            statement.setObject(1, completion.studentId().value());
            statement.setString(2, completion.learnedTechnique().type().name());
            statement.setString(3, completion.learnedTechnique().id().value());
            statement.setObject(4, completion.teachingSessionId());
            statement.setString(5, request.teacherReward().contentVersion());
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return readKnowledge(row);
            }
        }
    }

    private static RenownRecord mutateRenown(
            Connection connection,
            CharacterId characterId,
            Optional<RenownRecord> current,
            RenownDecision decision)
            throws SQLException {
        String sql =
                current.isEmpty()
                        ? "INSERT INTO character_renown(character_id, renown, version, updated_at) "
                                + "VALUES (?, ?, 1, CURRENT_TIMESTAMP) RETURNING "
                                + RENOWN_COLUMNS
                        : "UPDATE character_renown SET renown = ?, version = version + 1, "
                                + "updated_at = CURRENT_TIMESTAMP WHERE character_id = ? "
                                + "AND version = ? RETURNING "
                                + RENOWN_COLUMNS;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (current.isEmpty()) {
                statement.setObject(1, characterId.value());
                statement.setLong(2, decision.resultingRenown());
            } else {
                statement.setLong(1, decision.resultingRenown());
                statement.setObject(2, characterId.value());
                statement.setLong(3, current.orElseThrow().version());
            }
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException(
                            "Renown projection changed while participants were locked.");
                }
                return readRenown(row);
            }
        }
    }

    private static RenownDeedRecord insertDeed(
            Connection connection, RenownDeedCandidate candidate, RenownDecision decision)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO renown_deed_journal("
                                + DEED_COLUMNS
                                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) "
                                + "RETURNING recorded_at")) {
            statement.setObject(1, candidate.deedId());
            statement.setObject(2, candidate.characterId().value());
            statement.setString(3, candidate.deedType().value());
            statement.setString(4, candidate.noveltyFingerprint());
            statement.setInt(5, candidate.baseRenown());
            statement.setString(6, candidate.contentVersion());
            statement.setBoolean(7, decision.accepted());
            statement.setInt(8, decision.awardedRenown());
            statement.setLong(9, decision.resultingRenown());
            statement.setDouble(10, decision.repetitionFactor());
            statement.setString(11, decision.suppressionReason().name());
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return new RenownDeedRecord(
                        candidate,
                        decision,
                        row.getObject("recorded_at", OffsetDateTime.class).toInstant());
            }
        }
    }

    private static TeachingCompletionRecord insertTeaching(
            Connection connection, TeachingCommitRequest request) throws SQLException {
        TeachingCompletion completion = request.completion();
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "INSERT INTO teaching_completion_journal("
                                + TEACHING_COLUMNS
                                + ") VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) "
                                + "RETURNING completed_at")) {
            statement.setObject(1, completion.teachingSessionId());
            statement.setObject(2, completion.teacherId().value());
            statement.setObject(3, completion.studentId().value());
            statement.setString(4, completion.learnedTechnique().type().name());
            statement.setString(5, completion.learnedTechnique().id().value());
            statement.setObject(6, request.teacherReward().deedId());
            statement.setString(7, request.teacherReward().contentVersion());
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return new TeachingCompletionRecord(
                        completion,
                        request.teacherReward().deedId(),
                        request.teacherReward().contentVersion(),
                        row.getObject("completed_at", OffsetDateTime.class).toInstant());
            }
        }
    }

    private static int identicalDeedsToday(Connection connection, RenownDeedCandidate candidate)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        SELECT COUNT(*)
                        FROM renown_deed_journal
                        WHERE character_id = ?
                          AND novelty_fingerprint = ?
                          AND accepted
                          AND recorded_at >= (
                              date_trunc('day', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
                              AT TIME ZONE 'UTC'
                          )
                        """)) {
            statement.setObject(1, candidate.characterId().value());
            statement.setString(2, candidate.noveltyFingerprint());
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getInt(1);
            }
        }
    }

    private static List<KnowledgeRecord> findKnowledge(
            Connection connection, CharacterId characterId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT "
                                + KNOWLEDGE_COLUMNS
                                + " FROM character_knowledge WHERE character_id = ? "
                                + "ORDER BY knowledge_type, definition_id")) {
            statement.setObject(1, characterId.value());
            try (ResultSet row = statement.executeQuery()) {
                List<KnowledgeRecord> records = new ArrayList<>();
                while (row.next()) {
                    records.add(readKnowledge(row));
                }
                return List.copyOf(records);
            }
        }
    }

    private static Optional<KnowledgeRecord> findKnowledge(
            Connection connection, CharacterId characterId, KnowledgeKey key) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT "
                                + KNOWLEDGE_COLUMNS
                                + " FROM character_knowledge WHERE character_id = ? "
                                + "AND knowledge_type = ? AND definition_id = ?")) {
            statement.setObject(1, characterId.value());
            statement.setString(2, key.type().name());
            statement.setString(3, key.id().value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readKnowledge(row)) : Optional.empty();
            }
        }
    }

    private static Optional<RenownRecord> findRenown(Connection connection, CharacterId characterId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT "
                                + RENOWN_COLUMNS
                                + " FROM character_renown WHERE character_id = ?")) {
            statement.setObject(1, characterId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readRenown(row)) : Optional.empty();
            }
        }
    }

    private static Optional<RenownDeedRecord> findDeed(Connection connection, UUID deedId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT " + DEED_COLUMNS + " FROM renown_deed_journal WHERE deed_id = ?")) {
            statement.setObject(1, deedId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readDeed(row)) : Optional.empty();
            }
        }
    }

    private static Optional<TeachingCompletionRecord> findTeaching(
            Connection connection, UUID sessionId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT "
                                + TEACHING_COLUMNS
                                + " FROM teaching_completion_journal "
                                + "WHERE teaching_session_id = ?")) {
            statement.setObject(1, sessionId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readTeaching(row)) : Optional.empty();
            }
        }
    }

    private static KnowledgeRecord readKnowledge(ResultSet row) throws SQLException {
        return new KnowledgeRecord(
                new CharacterId(row.getObject("character_id", UUID.class)),
                new KnowledgeKey(
                        KnowledgeType.valueOf(row.getString("knowledge_type")),
                        DefinitionId.of(row.getString("definition_id"))),
                row.getString("source_type"),
                row.getObject("source_id", UUID.class),
                row.getString("content_version"),
                row.getObject("learned_at", OffsetDateTime.class).toInstant());
    }

    private static RenownRecord readRenown(ResultSet row) throws SQLException {
        return new RenownRecord(
                new CharacterId(row.getObject("character_id", UUID.class)),
                row.getLong("renown"),
                row.getLong("version"),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static RenownDeedRecord readDeed(ResultSet row) throws SQLException {
        RenownDeedCandidate candidate =
                new RenownDeedCandidate(
                        row.getObject("deed_id", UUID.class),
                        new CharacterId(row.getObject("character_id", UUID.class)),
                        DefinitionId.of(row.getString("deed_type")),
                        row.getString("novelty_fingerprint"),
                        row.getInt("base_renown"),
                        row.getString("content_version"));
        RenownDecision decision =
                new RenownDecision(
                        row.getBoolean("accepted"),
                        row.getInt("awarded_renown"),
                        row.getLong("resulting_renown"),
                        row.getDouble("repetition_factor"),
                        RenownSuppressionReason.valueOf(row.getString("suppression_reason")));
        return new RenownDeedRecord(
                candidate,
                decision,
                row.getObject("recorded_at", OffsetDateTime.class).toInstant());
    }

    private static TeachingCompletionRecord readTeaching(ResultSet row) throws SQLException {
        TeachingCompletion completion =
                new TeachingCompletion(
                        row.getObject("teaching_session_id", UUID.class),
                        new CharacterId(row.getObject("teacher_id", UUID.class)),
                        new CharacterId(row.getObject("student_id", UUID.class)),
                        new KnowledgeKey(
                                KnowledgeType.valueOf(row.getString("knowledge_type")),
                                DefinitionId.of(row.getString("definition_id"))));
        return new TeachingCompletionRecord(
                completion,
                row.getObject("deed_id", UUID.class),
                row.getString("content_version"),
                row.getObject("completed_at", OffsetDateTime.class).toInstant());
    }

    private static void lockParticipants(
            Connection connection, CharacterId teacherId, CharacterId studentId)
            throws SQLException {
        List<CharacterId> ordered =
                List.of(teacherId, studentId).stream()
                        .sorted(Comparator.comparing(id -> id.value().toString()))
                        .toList();
        for (CharacterId participant : ordered) {
            try (PreparedStatement statement =
                    connection.prepareStatement(
                            "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, "knowledge:" + participant.value());
                statement.executeQuery();
            }
        }
    }

    private static <T> Result<T, KnowledgePersistenceErrorCode> failure(Exception exception) {
        if (exception instanceof IllegalArgumentException) {
            return Result.failure(
                    KnowledgePersistenceErrorCode.KNOWLEDGE_STATE_INVALID,
                    "Persisted Knowledge/Renown state is invalid.");
        }
        return Result.failure(
                KnowledgePersistenceErrorCode.KNOWLEDGE_DATABASE_UNAVAILABLE,
                "PostgreSQL Knowledge/Renown operation failed.");
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original failure.
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean originalAutoCommit) {
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException ignored) {
            // Connection close follows immediately.
        }
    }
}
