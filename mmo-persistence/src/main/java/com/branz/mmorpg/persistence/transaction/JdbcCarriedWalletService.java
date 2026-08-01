package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Local durable carried-wallet authority with journaled idempotent debit/credit operations. */
public final class JdbcCarriedWalletService implements CarriedWalletService {
    public static final String CARRIED_WALLET_ADJUST = "wallet.carried.adjust";
    private final DataSource dataSource;

    public JdbcCarriedWalletService(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Result<CarriedWalletBalance, TransactionErrorCode> balance(CharacterId characterId) {
        Objects.requireNonNull(characterId, "characterId");
        try (Connection connection = dataSource.getConnection()) {
            return Result.success(
                    findBalance(connection, characterId, false)
                            .orElseGet(() -> CarriedWalletBalance.empty(characterId)));
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    @Override
    public Result<CarriedWalletAdjustmentExecution, TransactionErrorCode> adjust(
            TransactionRequest request, CarriedWalletAdjustment adjustment) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(adjustment, "adjustment");
        if (!request.operationType().equals(CARRIED_WALLET_ADJUST)) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_OPERATION_MISMATCH,
                    "Transaction operation does not match a carried-wallet adjustment.");
        }
        if (!request.transactionId().value().equals(adjustment.operationId())) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Wallet operation ID must equal its transaction ID.");
        }
        if (request.characterId().isPresent() || request.sessionId().isPresent()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Carried-wallet adjustments must use a system transaction.");
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                Result<JournalPrepareOutcome, TransactionErrorCode> preparedResult =
                        JdbcTransactionJournalRepository.prepare(connection, request);
                if (preparedResult
                        instanceof
                        Result.Failure<JournalPrepareOutcome, TransactionErrorCode> failure) {
                    connection.rollback();
                    return Result.failure(failure.error(), failure.detail());
                }
                JournalPrepareOutcome prepared =
                        ((Result.Success<JournalPrepareOutcome, TransactionErrorCode>)
                                        preparedResult)
                                .value();
                if (!prepared.newlyPrepared()) {
                    if (prepared.entry().state() != TransactionState.COMMITTED) {
                        connection.rollback();
                        return Result.failure(
                                TransactionErrorCode.TRANSACTION_INVALID_STATE,
                                "Existing carried-wallet transaction is not committed.");
                    }
                    WalletOperation operation = findOperation(connection, adjustment.operationId());
                    if (operation == null || !operation.matches(adjustment)) {
                        connection.rollback();
                        return Result.failure(
                                TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                                "Committed wallet operation differs from the retry.");
                    }
                    CarriedWalletBalance balance =
                            findBalance(connection, adjustment.characterId(), false)
                                    .orElseThrow(
                                            () ->
                                                    new SQLException(
                                                            "Committed wallet account is missing."));
                    connection.commit();
                    return Result.success(
                            new CarriedWalletAdjustmentExecution(
                                    balance, new TransactionExecution(prepared.entry(), true)));
                }
                lockCharacter(connection, adjustment.characterId());
                CarriedWalletBalance current =
                        findBalance(connection, adjustment.characterId(), true)
                                .orElseGet(
                                        () -> CarriedWalletBalance.empty(adjustment.characterId()));
                long next;
                try {
                    next =
                            adjustment.kind() == CarriedWalletOperationKind.CREDIT
                                    ? Math.addExact(current.balance(), adjustment.amount())
                                    : Math.subtractExact(current.balance(), adjustment.amount());
                } catch (ArithmeticException exception) {
                    connection.rollback();
                    return Result.failure(
                            adjustment.kind() == CarriedWalletOperationKind.DEBIT
                                    ? TransactionErrorCode.VALUE_INSUFFICIENT_QUANTITY
                                    : TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                            "Carried-wallet adjustment exceeds the valid balance range.");
                }
                if (next < 0) {
                    connection.rollback();
                    return Result.failure(
                            TransactionErrorCode.VALUE_INSUFFICIENT_QUANTITY,
                            "Carried wallet has insufficient balance.");
                }
                long nextVersion;
                try {
                    nextVersion = Math.addExact(current.version(), 1);
                } catch (ArithmeticException exception) {
                    connection.rollback();
                    return Result.failure(
                            TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                            "Carried-wallet version exceeds the valid range.");
                }
                writeBalance(connection, request, adjustment.characterId(), next, nextVersion);
                insertOperation(connection, request, adjustment, next);
                appendAudit(connection, request, adjustment, next, nextVersion);
                Result<JournalTransitionOutcome, TransactionErrorCode> transitioned =
                        JdbcTransactionJournalRepository.transition(
                                connection, request.transactionId(), TransactionState.COMMITTED);
                if (transitioned
                        instanceof
                        Result.Failure<JournalTransitionOutcome, TransactionErrorCode> failure) {
                    connection.rollback();
                    return Result.failure(failure.error(), failure.detail());
                }
                TransactionJournalEntry journal =
                        ((Result.Success<JournalTransitionOutcome, TransactionErrorCode>)
                                        transitioned)
                                .value()
                                .entry();
                connection.commit();
                return Result.success(
                        new CarriedWalletAdjustmentExecution(
                                new CarriedWalletBalance(
                                        adjustment.characterId(),
                                        next,
                                        nextVersion,
                                        Optional.of(request.transactionId())),
                                new TransactionExecution(journal, false)));
            } catch (SQLException exception) {
                JdbcTransactionJournalRepository.rollbackQuietly(connection);
                return JdbcTransactionJournalRepository.failure(exception);
            } finally {
                JdbcTransactionJournalRepository.restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    private static Optional<CarriedWalletBalance> findBalance(
            Connection connection, CharacterId characterId, boolean lock) throws SQLException {
        String sql =
                "SELECT balance, version, last_transaction_id FROM carried_wallet_account"
                        + " WHERE character_id = ?"
                        + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, characterId.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(
                        new CarriedWalletBalance(
                                characterId,
                                row.getLong("balance"),
                                row.getLong("version"),
                                Optional.of(
                                        new TransactionId(
                                                row.getObject(
                                                        "last_transaction_id", UUID.class)))));
            }
        }
    }

    private static void lockCharacter(Connection connection, CharacterId characterId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, "carried-wallet:" + characterId.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("Carried-wallet advisory lock returned no row.");
                }
            }
        }
    }

    private static void writeBalance(
            Connection connection,
            TransactionRequest request,
            CharacterId characterId,
            long balance,
            long version)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO carried_wallet_account(
                            character_id, balance, version, last_transaction_id,
                            created_at, updated_at
                        ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT (character_id) DO UPDATE SET
                            balance = EXCLUDED.balance,
                            version = EXCLUDED.version,
                            last_transaction_id = EXCLUDED.last_transaction_id,
                            updated_at = CURRENT_TIMESTAMP
                        """)) {
            statement.setObject(1, characterId.value());
            statement.setLong(2, balance);
            statement.setLong(3, version);
            statement.setObject(4, request.transactionId().value());
            statement.executeUpdate();
        }
    }

    private static void insertOperation(
            Connection connection,
            TransactionRequest request,
            CarriedWalletAdjustment adjustment,
            long resultingBalance)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO carried_wallet_operation(
                            operation_id, character_id, kind, amount, resulting_balance,
                            transaction_id, created_at
                        ) VALUES (?, ?, CAST(? AS carried_wallet_operation_kind), ?, ?, ?,
                            CURRENT_TIMESTAMP)
                        """)) {
            statement.setObject(1, adjustment.operationId());
            statement.setObject(2, adjustment.characterId().value());
            statement.setString(3, adjustment.kind().name());
            statement.setLong(4, adjustment.amount());
            statement.setLong(5, resultingBalance);
            statement.setObject(6, request.transactionId().value());
            statement.executeUpdate();
        }
    }

    private static WalletOperation findOperation(Connection connection, UUID operationId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        SELECT character_id, kind, amount, resulting_balance
                        FROM carried_wallet_operation WHERE operation_id = ?
                        """)) {
            statement.setObject(1, operationId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next()
                        ? new WalletOperation(
                                new CharacterId(row.getObject("character_id", UUID.class)),
                                CarriedWalletOperationKind.valueOf(row.getString("kind")),
                                row.getLong("amount"),
                                row.getLong("resulting_balance"))
                        : null;
            }
        }
    }

    private static void appendAudit(
            Connection connection,
            TransactionRequest request,
            CarriedWalletAdjustment adjustment,
            long resultingBalance,
            long version)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO audit_log(
                            transaction_id, actor_character_id, action_type,
                            subject_type, subject_id, details, created_at
                        ) VALUES (?, NULL, ?, 'CHARACTER', ?, CAST(? AS JSONB), CURRENT_TIMESTAMP)
                        """)) {
            statement.setObject(1, request.transactionId().value());
            statement.setString(2, CARRIED_WALLET_ADJUST);
            statement.setObject(3, adjustment.characterId().value());
            statement.setString(
                    4,
                    "{\"kind\":\""
                            + adjustment.kind()
                            + "\",\"amount\":"
                            + adjustment.amount()
                            + ",\"resultingBalance\":"
                            + resultingBalance
                            + ",\"version\":"
                            + version
                            + "}");
            statement.executeUpdate();
        }
    }

    private record WalletOperation(
            CharacterId characterId,
            CarriedWalletOperationKind kind,
            long amount,
            long resultingBalance) {
        boolean matches(CarriedWalletAdjustment adjustment) {
            return characterId.equals(adjustment.characterId())
                    && kind == adjustment.kind()
                    && amount == adjustment.amount();
        }
    }
}
