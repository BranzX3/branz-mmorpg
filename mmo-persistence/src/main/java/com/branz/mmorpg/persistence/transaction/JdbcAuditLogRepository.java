package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
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

/** Read-only audit inspection repository. */
public final class JdbcAuditLogRepository implements AuditLogRepository {
    private final DataSource dataSource;

    public JdbcAuditLogRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Result<List<AuditLogEntry>, TransactionErrorCode> findByTransaction(
            TransactionId transactionId) {
        Objects.requireNonNull(transactionId, "transactionId");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                SELECT audit_id, transaction_id, actor_character_id,
                                       action_type, subject_type, subject_id,
                                       details, created_at
                                FROM audit_log
                                WHERE transaction_id = ?
                                ORDER BY audit_id
                                """)) {
            statement.setObject(1, transactionId.value());
            try (ResultSet row = statement.executeQuery()) {
                List<AuditLogEntry> entries = new ArrayList<>();
                while (row.next()) {
                    UUID actorId = row.getObject("actor_character_id", UUID.class);
                    entries.add(
                            new AuditLogEntry(
                                    row.getLong("audit_id"),
                                    new TransactionId(row.getObject("transaction_id", UUID.class)),
                                    Optional.ofNullable(actorId).map(CharacterId::new),
                                    row.getString("action_type"),
                                    AuditSubjectType.valueOf(row.getString("subject_type")),
                                    row.getObject("subject_id", UUID.class),
                                    row.getString("details"),
                                    row.getObject("created_at", OffsetDateTime.class).toInstant()));
                }
                return Result.success(List.copyOf(entries));
            }
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }
}
