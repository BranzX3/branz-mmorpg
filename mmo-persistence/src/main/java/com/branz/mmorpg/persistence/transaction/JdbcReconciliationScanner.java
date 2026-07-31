package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.result.Result;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Read-only detector for recoverable persistence anomalies. It never guesses a repair or moves
 * value.
 */
public final class JdbcReconciliationScanner implements ReconciliationScanner {
    private static final String KNOWN_LOCATIONS =
            """
            'CHARACTER_INVENTORY', 'NATIVE_EQUIPPED', 'VIRTUAL_EQUIPPED',
            'QUIVER', 'PENDING_REWARDS', 'OVERFLOW_CLAIM', 'QUARANTINE', 'DESTROYED'
            """;

    private final DataSource dataSource;

    public JdbcReconciliationScanner(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Result<ReconciliationReport, ReconciliationErrorCode> scan(
            Duration stalePreparedAge, int limit) {
        Objects.requireNonNull(stalePreparedAge, "stalePreparedAge");
        long ageMillis = stalePreparedAge.toMillis();
        if (ageMillis < 1) {
            throw new IllegalArgumentException("stalePreparedAge must be positive");
        }
        if (limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("limit must be between 1 and 10000");
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                WITH issues AS (
                                    SELECT
                                        'STALE_PREPARED_TRANSACTION' AS code,
                                        'TRANSACTION' AS subject_type,
                                        transaction_id AS subject_id,
                                        'Prepared transaction exceeded reconciliation age' AS detail,
                                        updated_at AS observed_at
                                    FROM transaction_journal
                                    WHERE state = 'PREPARED'
                                      AND updated_at <= CURRENT_TIMESTAMP
                                          - (? * INTERVAL '1 millisecond')

                                    UNION ALL

                                    SELECT
                                        'ITEM_UNCOMMITTED_TRANSACTION',
                                        'ITEM',
                                        item.item_uuid,
                                        'Last item transaction is not committed',
                                        item.updated_at
                                    FROM item_instance item
                                    JOIN transaction_journal journal
                                      ON journal.transaction_id = item.last_transaction_id
                                    WHERE journal.state <> 'COMMITTED'

                                    UNION ALL

                                    SELECT
                                        'LOT_UNCOMMITTED_TRANSACTION',
                                        'LOT',
                                        lot.lot_uuid,
                                        'Last lot transaction is not committed',
                                        lot.updated_at
                                    FROM commodity_lot lot
                                    JOIN transaction_journal journal
                                      ON journal.transaction_id = lot.last_transaction_id
                                    WHERE journal.state <> 'COMMITTED'

                                    UNION ALL

                                    SELECT
                                        'UNKNOWN_ITEM_LOCATION',
                                        'ITEM',
                                        item_uuid,
                                        'Item location type is not recognized by this runtime',
                                        updated_at
                                    FROM item_instance
                                    WHERE location_type NOT IN (
                                """
                                        + KNOWN_LOCATIONS
                                        + """
                                    )

                                    UNION ALL

                                    SELECT
                                        'UNKNOWN_LOT_LOCATION',
                                        'LOT',
                                        lot_uuid,
                                        'Lot location type is not recognized by this runtime',
                                        updated_at
                                    FROM commodity_lot
                                    WHERE location_type NOT IN (
                                """
                                        + KNOWN_LOCATIONS
                                        + """
                                    )

                                    UNION ALL

                                    SELECT
                                        'ITEM_MISSING_OWNER',
                                        'ITEM',
                                        item_uuid,
                                        'Non-quarantine item location has no owner',
                                        updated_at
                                    FROM item_instance
                                    WHERE owner_character_id IS NULL
                                      AND location_type <> 'QUARANTINE'

                                    UNION ALL

                                    SELECT
                                        'LOT_MISSING_OWNER',
                                        'LOT',
                                        lot_uuid,
                                        'Non-quarantine lot location has no owner',
                                        updated_at
                                    FROM commodity_lot
                                    WHERE owner_character_id IS NULL
                                      AND location_type <> 'QUARANTINE'

                                    UNION ALL

                                    SELECT
                                        'EMPTY_LOT',
                                        'LOT',
                                        lot_uuid,
                                        'Persisted lot has zero quantity',
                                        updated_at
                                    FROM commodity_lot
                                    WHERE quantity = 0
                                      AND location_type <> 'DESTROYED'
                                )
                                SELECT code, subject_type, subject_id, detail, observed_at,
                                       CURRENT_TIMESTAMP AS scanned_at
                                FROM issues
                                ORDER BY code, subject_type, subject_id
                                LIMIT ?
                                """)) {
            statement.setLong(1, ageMillis);
            statement.setInt(2, limit);
            try (ResultSet row = statement.executeQuery()) {
                List<ReconciliationIssue> issues = new ArrayList<>();
                java.time.Instant scannedAt = null;
                while (row.next()) {
                    scannedAt = row.getObject("scanned_at", OffsetDateTime.class).toInstant();
                    issues.add(
                            new ReconciliationIssue(
                                    ReconciliationIssueCode.valueOf(row.getString("code")),
                                    AuditSubjectType.valueOf(row.getString("subject_type")),
                                    row.getObject("subject_id", UUID.class),
                                    row.getString("detail"),
                                    row.getObject("observed_at", OffsetDateTime.class)
                                            .toInstant()));
                }
                if (scannedAt == null) {
                    scannedAt = databaseTime(connection);
                }
                return Result.success(new ReconciliationReport(scannedAt, issues));
            }
        } catch (SQLException exception) {
            String state = exception.getSQLState();
            return Result.failure(
                    ReconciliationErrorCode.RECONCILIATION_DATABASE_UNAVAILABLE,
                    exception.getClass().getSimpleName()
                            + (state == null ? "" : " SQLSTATE=" + state));
        }
    }

    private static java.time.Instant databaseTime(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT CURRENT_TIMESTAMP");
                ResultSet row = statement.executeQuery()) {
            row.next();
            return row.getObject(1, OffsetDateTime.class).toInstant();
        }
    }
}
