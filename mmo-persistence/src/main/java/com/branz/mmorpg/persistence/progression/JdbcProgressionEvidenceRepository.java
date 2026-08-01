package com.branz.mmorpg.persistence.progression;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.progression.evidence.EncounterOutcome;
import com.branz.mmorpg.progression.evidence.EvidenceCandidate;
import com.branz.mmorpg.progression.evidence.EvidenceContext;
import com.branz.mmorpg.progression.evidence.EvidenceDecision;
import com.branz.mmorpg.progression.evidence.EvidenceFactorBreakdown;
import com.branz.mmorpg.progression.evidence.EvidenceSuppressionReason;
import com.branz.mmorpg.progression.evidence.EvidenceTargetKind;
import com.branz.mmorpg.progression.evidence.ProgressionEvidenceEngine;
import com.branz.mmorpg.progression.evidence.ProgressionTrack;
import com.branz.mmorpg.progression.evidence.ProgressionTrackType;
import com.branz.mmorpg.progression.evidence.ReadinessBand;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** PostgreSQL-authoritative evidence journal and per-track projection. */
public final class JdbcProgressionEvidenceRepository implements ProgressionEvidenceRepository {
    private static final int MAXIMUM_BATCH_SIZE = 256;
    private static final String EVIDENCE_COLUMNS =
            """
            evidence_id, batch_id, character_id, encounter_id, track_id, track_type,
            novelty_fingerprint, content_version, target_kind, outcome, base_evidence,
            challenge_rating, demonstrated_capability, move_diversity_ratio,
            execution_quality, stress_ratio, accepted, awarded_evidence, resulting_evidence,
            previous_band, resulting_band, suppression_reason, factor_challenge,
            factor_outcome, factor_diversity, factor_execution, factor_novelty,
            factor_repetition, factor_risk, factor_daily_curve, recorded_at
            """;
    private static final String TRACK_COLUMNS =
            "character_id, track_id, track_type, evidence, version, updated_at";

    private final DataSource dataSource;
    private final ProgressionEvidenceEngine evidenceEngine;

    public JdbcProgressionEvidenceRepository(DataSource dataSource) {
        this(dataSource, new ProgressionEvidenceEngine());
    }

    public JdbcProgressionEvidenceRepository(
            DataSource dataSource, ProgressionEvidenceEngine evidenceEngine) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.evidenceEngine = Objects.requireNonNull(evidenceEngine, "evidenceEngine");
    }

    @Override
    public Result<List<ProgressionTrackRecord>, ProgressionPersistenceErrorCode> findTracks(
            CharacterId characterId) {
        Objects.requireNonNull(characterId, "characterId");
        try (Connection connection = dataSource.getConnection()) {
            return Result.success(findTracks(connection, characterId));
        } catch (SQLException exception) {
            return databaseFailure(exception);
        }
    }

    @Override
    public Result<List<ProgressionEvidenceExecution>, ProgressionPersistenceErrorCode> recordBatch(
            List<EvidenceCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        List<EvidenceCandidate> immutable;
        try {
            immutable = List.copyOf(candidates);
        } catch (NullPointerException exception) {
            return Result.failure(
                    ProgressionPersistenceErrorCode.PROGRESSION_BATCH_INVALID,
                    "Progression batch contains a null candidate.");
        }
        if (immutable.isEmpty() || immutable.size() > MAXIMUM_BATCH_SIZE) {
            return Result.failure(
                    ProgressionPersistenceErrorCode.PROGRESSION_BATCH_INVALID,
                    "Progression batch must contain between 1 and 256 candidates.");
        }
        CharacterId owner = immutable.getFirst().characterId();
        if (immutable.stream().anyMatch(candidate -> !candidate.characterId().equals(owner))) {
            return Result.failure(
                    ProgressionPersistenceErrorCode.PROGRESSION_BATCH_INVALID,
                    "One progression batch may contain only one character.");
        }

        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                lockCharacter(connection, owner);
                UUID batchId = UUID.randomUUID();
                List<ProgressionEvidenceExecution> executions = new ArrayList<>();
                for (EvidenceCandidate candidate : immutable) {
                    Result<ProgressionEvidenceExecution, ProgressionPersistenceErrorCode> recorded =
                            recordOne(connection, batchId, candidate);
                    if (recorded
                            instanceof
                            Result.Failure<
                                            ProgressionEvidenceExecution,
                                            ProgressionPersistenceErrorCode>
                                    failure) {
                        connection.rollback();
                        return Result.failure(failure.error(), failure.detail());
                    }
                    executions.add(
                            ((Result.Success<
                                                    ProgressionEvidenceExecution,
                                                    ProgressionPersistenceErrorCode>)
                                            recorded)
                                    .value());
                }
                connection.commit();
                return Result.success(List.copyOf(executions));
            } catch (SQLException | IllegalArgumentException exception) {
                rollbackQuietly(connection);
                if (exception instanceof SQLException sqlException) {
                    return databaseFailure(sqlException);
                }
                return Result.failure(
                        ProgressionPersistenceErrorCode.PROGRESSION_STATE_INVALID,
                        "Persisted progression state is invalid.");
            } finally {
                restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (SQLException exception) {
            return databaseFailure(exception);
        }
    }

    private Result<ProgressionEvidenceExecution, ProgressionPersistenceErrorCode> recordOne(
            Connection connection, UUID batchId, EvidenceCandidate candidate) throws SQLException {
        Optional<ProgressionEvidenceRecord> existing =
                findEvidence(connection, candidate.evidenceId());
        if (existing.isPresent()) {
            ProgressionEvidenceRecord record = existing.orElseThrow();
            if (!record.candidate().equals(candidate)) {
                return Result.failure(
                        ProgressionPersistenceErrorCode.PROGRESSION_EVIDENCE_ID_CONFLICT,
                        "Evidence UUID is already bound to a different immutable candidate.");
            }
            return Result.success(
                    new ProgressionEvidenceExecution(
                            record,
                            findTrack(connection, candidate.characterId(), candidate.track()),
                            true));
        }

        Optional<ProgressionTrackRecord> current =
                findTrack(connection, candidate.characterId(), candidate.track());
        EvidenceContext context =
                new EvidenceContext(
                        current.map(ProgressionTrackRecord::evidence).orElse(0.0),
                        identicalCompletions(connection, candidate),
                        acceptedEvidenceToday(connection, candidate),
                        false);
        EvidenceDecision decision = evidenceEngine.evaluate(candidate, context);
        Optional<ProgressionTrackRecord> resultingTrack = current;
        if (decision.accepted()) {
            resultingTrack = Optional.of(mutateTrack(connection, candidate, current, decision));
        }
        ProgressionEvidenceRecord record = insertEvidence(connection, batchId, candidate, decision);
        return Result.success(new ProgressionEvidenceExecution(record, resultingTrack, false));
    }

    private static void lockCharacter(Connection connection, CharacterId characterId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, characterId.value().toString());
            statement.executeQuery();
        }
    }

    private static int identicalCompletions(Connection connection, EvidenceCandidate candidate)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        SELECT COUNT(*)
                        FROM combat_progression_evidence
                        WHERE character_id = ?
                          AND track_id = ?
                          AND novelty_fingerprint = ?
                          AND accepted
                          AND recorded_at >= CURRENT_TIMESTAMP - INTERVAL '30 minutes'
                        """)) {
            statement.setObject(1, candidate.characterId().value());
            statement.setString(2, candidate.track().id().value());
            statement.setString(3, candidate.noveltyFingerprint());
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getInt(1);
            }
        }
    }

    private static double acceptedEvidenceToday(Connection connection, EvidenceCandidate candidate)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        SELECT COALESCE(SUM(awarded_evidence), 0)
                        FROM combat_progression_evidence
                        WHERE character_id = ?
                          AND track_id = ?
                          AND accepted
                          AND recorded_at >= (
                              date_trunc('day', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
                              AT TIME ZONE 'UTC'
                          )
                        """)) {
            statement.setObject(1, candidate.characterId().value());
            statement.setString(2, candidate.track().id().value());
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getDouble(1);
            }
        }
    }

    private static ProgressionTrackRecord mutateTrack(
            Connection connection,
            EvidenceCandidate candidate,
            Optional<ProgressionTrackRecord> current,
            EvidenceDecision decision)
            throws SQLException {
        String sql =
                current.isEmpty()
                        ? """
                        INSERT INTO character_progression_track(
                            character_id, track_id, track_type, evidence, version, updated_at
                        ) VALUES (?, ?, ?, ?, 1, CURRENT_TIMESTAMP)
                        RETURNING
                        """
                                + TRACK_COLUMNS
                        : """
                        UPDATE character_progression_track
                        SET evidence = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
                        WHERE character_id = ? AND track_id = ? AND version = ?
                        RETURNING
                        """
                                + TRACK_COLUMNS;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (current.isEmpty()) {
                statement.setObject(1, candidate.characterId().value());
                statement.setString(2, candidate.track().id().value());
                statement.setString(3, candidate.track().type().name());
                statement.setDouble(4, decision.resultingEvidence());
            } else {
                statement.setDouble(1, decision.resultingEvidence());
                statement.setObject(2, candidate.characterId().value());
                statement.setString(3, candidate.track().id().value());
                statement.setLong(4, current.orElseThrow().version());
            }
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException(
                            "Progression track changed while holding character lock.");
                }
                return readTrack(row);
            }
        }
    }

    private static ProgressionEvidenceRecord insertEvidence(
            Connection connection,
            UUID batchId,
            EvidenceCandidate candidate,
            EvidenceDecision decision)
            throws SQLException {
        String sql =
                """
                INSERT INTO combat_progression_evidence(
                    evidence_id, batch_id, character_id, encounter_id, track_id, track_type,
                    novelty_fingerprint, content_version, target_kind, outcome, base_evidence,
                    challenge_rating, demonstrated_capability, move_diversity_ratio,
                    execution_quality, stress_ratio, accepted, awarded_evidence,
                    resulting_evidence, previous_band, resulting_band, suppression_reason,
                    factor_challenge, factor_outcome, factor_diversity, factor_execution,
                    factor_novelty, factor_repetition, factor_risk, factor_daily_curve, recorded_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, CURRENT_TIMESTAMP
                )
                RETURNING recorded_at
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindCandidate(statement, candidate);
            statement.setBoolean(index++, decision.accepted());
            statement.setDouble(index++, decision.awardedEvidence());
            statement.setDouble(index++, decision.resultingEvidence());
            statement.setString(index++, decision.previousBand().name());
            statement.setString(index++, decision.resultingBand().name());
            statement.setString(index++, decision.suppressionReason().name());
            EvidenceFactorBreakdown factors = decision.factors();
            statement.setDouble(index++, factors.challenge());
            statement.setDouble(index++, factors.outcome());
            statement.setDouble(index++, factors.diversity());
            statement.setDouble(index++, factors.execution());
            statement.setDouble(index++, factors.novelty());
            statement.setDouble(index++, factors.repetition());
            statement.setDouble(index++, factors.risk());
            statement.setDouble(index, factors.dailyCurve());
            statement.setObject(1, candidate.evidenceId());
            statement.setObject(2, batchId);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return new ProgressionEvidenceRecord(
                        batchId,
                        candidate,
                        decision,
                        row.getObject("recorded_at", OffsetDateTime.class).toInstant());
            }
        }
    }

    private static int bindCandidate(PreparedStatement statement, EvidenceCandidate candidate)
            throws SQLException {
        statement.setObject(3, candidate.characterId().value());
        statement.setObject(4, candidate.encounterId().value());
        statement.setString(5, candidate.track().id().value());
        statement.setString(6, candidate.track().type().name());
        statement.setString(7, candidate.noveltyFingerprint());
        statement.setString(8, candidate.contentVersion());
        statement.setString(9, candidate.targetKind().name());
        statement.setString(10, candidate.outcome().name());
        statement.setDouble(11, candidate.baseEvidence());
        statement.setDouble(12, candidate.challengeRating());
        statement.setDouble(13, candidate.demonstratedCapability());
        statement.setDouble(14, candidate.moveDiversityRatio());
        statement.setDouble(15, candidate.executionQuality());
        statement.setDouble(16, candidate.stressRatio());
        return 17;
    }

    private static Optional<ProgressionEvidenceRecord> findEvidence(
            Connection connection, UUID evidenceId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT "
                                + EVIDENCE_COLUMNS
                                + " FROM combat_progression_evidence WHERE evidence_id = ?")) {
            statement.setObject(1, evidenceId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readEvidence(row)) : Optional.empty();
            }
        }
    }

    private static List<ProgressionTrackRecord> findTracks(
            Connection connection, CharacterId characterId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT "
                                + TRACK_COLUMNS
                                + " FROM character_progression_track"
                                + " WHERE character_id = ? ORDER BY track_id")) {
            statement.setObject(1, characterId.value());
            try (ResultSet row = statement.executeQuery()) {
                List<ProgressionTrackRecord> tracks = new ArrayList<>();
                while (row.next()) {
                    tracks.add(readTrack(row));
                }
                return List.copyOf(tracks);
            }
        }
    }

    private static Optional<ProgressionTrackRecord> findTrack(
            Connection connection, CharacterId characterId, ProgressionTrack track)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT "
                                + TRACK_COLUMNS
                                + " FROM character_progression_track"
                                + " WHERE character_id = ? AND track_id = ?")) {
            statement.setObject(1, characterId.value());
            statement.setString(2, track.id().value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readTrack(row)) : Optional.empty();
            }
        }
    }

    private static ProgressionTrackRecord readTrack(ResultSet row) throws SQLException {
        return new ProgressionTrackRecord(
                new CharacterId(row.getObject("character_id", UUID.class)),
                new ProgressionTrack(
                        DefinitionId.of(row.getString("track_id")),
                        ProgressionTrackType.valueOf(row.getString("track_type"))),
                row.getDouble("evidence"),
                row.getLong("version"),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static ProgressionEvidenceRecord readEvidence(ResultSet row) throws SQLException {
        EvidenceCandidate candidate =
                new EvidenceCandidate(
                        row.getObject("evidence_id", UUID.class),
                        new CharacterId(row.getObject("character_id", UUID.class)),
                        new EncounterId(row.getObject("encounter_id", UUID.class)),
                        new ProgressionTrack(
                                DefinitionId.of(row.getString("track_id")),
                                ProgressionTrackType.valueOf(row.getString("track_type"))),
                        row.getString("novelty_fingerprint"),
                        row.getString("content_version"),
                        EvidenceTargetKind.valueOf(row.getString("target_kind")),
                        EncounterOutcome.valueOf(row.getString("outcome")),
                        row.getDouble("base_evidence"),
                        row.getDouble("challenge_rating"),
                        row.getDouble("demonstrated_capability"),
                        row.getDouble("move_diversity_ratio"),
                        row.getDouble("execution_quality"),
                        row.getDouble("stress_ratio"));
        EvidenceDecision decision =
                new EvidenceDecision(
                        row.getBoolean("accepted"),
                        row.getDouble("awarded_evidence"),
                        row.getDouble("resulting_evidence"),
                        ReadinessBand.valueOf(row.getString("previous_band")),
                        ReadinessBand.valueOf(row.getString("resulting_band")),
                        EvidenceSuppressionReason.valueOf(row.getString("suppression_reason")),
                        new EvidenceFactorBreakdown(
                                row.getDouble("factor_challenge"),
                                row.getDouble("factor_outcome"),
                                row.getDouble("factor_diversity"),
                                row.getDouble("factor_execution"),
                                row.getDouble("factor_novelty"),
                                row.getDouble("factor_repetition"),
                                row.getDouble("factor_risk"),
                                row.getDouble("factor_daily_curve")));
        return new ProgressionEvidenceRecord(
                row.getObject("batch_id", UUID.class),
                candidate,
                decision,
                row.getObject("recorded_at", OffsetDateTime.class).toInstant());
    }

    private static <T> Result<T, ProgressionPersistenceErrorCode> databaseFailure(
            SQLException exception) {
        String state = exception.getSQLState();
        return Result.failure(
                ProgressionPersistenceErrorCode.PROGRESSION_DATABASE_UNAVAILABLE,
                "PostgreSQL progression operation failed"
                        + (state == null ? "." : " (SQLState " + state + ")."));
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The original failure remains authoritative.
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            // The connection is closing and cannot be recovered by this repository.
        }
    }
}
