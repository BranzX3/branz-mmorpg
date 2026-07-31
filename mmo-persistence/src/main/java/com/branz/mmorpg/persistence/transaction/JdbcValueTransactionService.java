package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Blocking transaction boundary for the foundational item/lot location operations.
 *
 * <p>The journal and value mutation commit in one PostgreSQL transaction. Callers must keep this
 * service off Paper server threads.
 */
public final class JdbcValueTransactionService implements ValueTransactionService {
    public static final String ITEM_GRANT = "item.grant";
    public static final String LOT_GRANT = "lot.grant";
    public static final String ITEM_MOVE = "item.move";
    public static final String ITEM_BATCH_MOVE = "item.move.batch";
    public static final String LOT_MOVE = "lot.move";
    public static final String LOT_CONSUME = "lot.consume";

    private static final String ITEM_COLUMNS =
            """
            item_uuid, definition_id, owner_character_id, location_type, location_ref,
            payload, content_version, version, last_transaction_id, created_at, updated_at
            """;
    private static final String LOT_COLUMNS =
            """
            lot_uuid, definition_id, variant, quantity, owner_character_id,
            location_type, location_ref, lineage, content_version, version,
            last_transaction_id, created_at, updated_at
            """;

    private final DataSource dataSource;
    private final CheckpointObserver checkpointObserver;

    public JdbcValueTransactionService(DataSource dataSource) {
        this(dataSource, checkpoint -> {});
    }

    JdbcValueTransactionService(DataSource dataSource, CheckpointObserver checkpointObserver) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.checkpointObserver = Objects.requireNonNull(checkpointObserver, "checkpointObserver");
    }

    @Override
    public Result<TransactionExecution, TransactionErrorCode> grantItem(
            TransactionRequest request, NewItemLocation item) {
        Objects.requireNonNull(item, "item");
        return execute(
                request,
                ITEM_GRANT,
                AuditSubjectType.ITEM,
                item.itemId().value(),
                connection -> insertItem(connection, request, item));
    }

    @Override
    public Result<TransactionExecution, TransactionErrorCode> grantLot(
            TransactionRequest request, NewLotLocation lot) {
        Objects.requireNonNull(lot, "lot");
        return execute(
                request,
                LOT_GRANT,
                AuditSubjectType.LOT,
                lot.lotId().value(),
                connection -> insertLot(connection, request, lot));
    }

    @Override
    public Result<TransactionExecution, TransactionErrorCode> moveItem(
            TransactionRequest request, ItemLocationMove move) {
        Objects.requireNonNull(move, "move");
        return execute(
                request,
                ITEM_MOVE,
                AuditSubjectType.ITEM,
                move.itemId().value(),
                connection -> updateItemLocation(connection, request.transactionId(), move));
    }

    @Override
    public Result<TransactionExecution, TransactionErrorCode> moveItemsAtomically(
            TransactionRequest request, List<ItemLocationMove> moves) {
        Objects.requireNonNull(moves, "moves");
        if (moves.isEmpty()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Atomic item move requires at least one item.");
        }
        List<ItemLocationMove> ordered =
                moves.stream()
                        .map(move -> Objects.requireNonNull(move, "move"))
                        .sorted(Comparator.comparing(move -> move.itemId().value()))
                        .toList();
        HashSet<ItemId> itemIds = new HashSet<>();
        if (ordered.stream().map(ItemLocationMove::itemId).anyMatch(id -> !itemIds.add(id))) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Atomic item move contains a duplicate item UUID.");
        }
        return execute(
                request,
                ITEM_BATCH_MOVE,
                AuditSubjectType.TRANSACTION,
                request.transactionId().value(),
                connection -> {
                    for (ItemLocationMove move : ordered) {
                        Result<MutationApplied, TransactionErrorCode> result =
                                updateItemLocation(connection, request.transactionId(), move);
                        if (!result.isSuccess()) {
                            return result;
                        }
                    }
                    return Result.success(MutationApplied.INSTANCE);
                });
    }

    @Override
    public Result<TransactionExecution, TransactionErrorCode> moveLot(
            TransactionRequest request, LotLocationMove move) {
        Objects.requireNonNull(move, "move");
        return execute(
                request,
                LOT_MOVE,
                AuditSubjectType.LOT,
                move.lotId().value(),
                connection -> updateLotLocation(connection, request.transactionId(), move));
    }

    @Override
    public Result<TransactionExecution, TransactionErrorCode> consumeLot(
            TransactionRequest request, LotQuantityConsumption consumption) {
        Objects.requireNonNull(consumption, "consumption");
        return execute(
                request,
                LOT_CONSUME,
                AuditSubjectType.LOT,
                consumption.lotId().value(),
                connection -> updateLotQuantity(connection, request.transactionId(), consumption));
    }

    @Override
    public Result<Optional<ItemLocationRecord>, TransactionErrorCode> findItem(ItemId itemId) {
        Objects.requireNonNull(itemId, "itemId");
        try (Connection connection = dataSource.getConnection()) {
            return Result.success(findItem(connection, itemId));
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    @Override
    public Result<Optional<LotLocationRecord>, TransactionErrorCode> findLot(LotId lotId) {
        Objects.requireNonNull(lotId, "lotId");
        try (Connection connection = dataSource.getConnection()) {
            return Result.success(findLot(connection, lotId));
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    @Override
    public Result<List<ItemLocationRecord>, TransactionErrorCode> findItemsOwnedBy(
            CharacterId characterId) {
        Objects.requireNonNull(characterId, "characterId");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT "
                                        + ITEM_COLUMNS
                                        + """
                                         FROM item_instance
                                         WHERE owner_character_id = ?
                                         ORDER BY location_type, location_ref NULLS FIRST, item_uuid
                                        """)) {
            statement.setObject(1, characterId.value());
            try (ResultSet row = statement.executeQuery()) {
                List<ItemLocationRecord> items = new ArrayList<>();
                while (row.next()) {
                    items.add(readItem(row));
                }
                return Result.success(List.copyOf(items));
            }
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    @Override
    public Result<List<LotLocationRecord>, TransactionErrorCode> findLotsOwnedBy(
            CharacterId characterId) {
        Objects.requireNonNull(characterId, "characterId");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT "
                                        + LOT_COLUMNS
                                        + """
                                         FROM commodity_lot
                                         WHERE owner_character_id = ?
                                         ORDER BY location_type, location_ref NULLS FIRST, lot_uuid
                                        """)) {
            statement.setObject(1, characterId.value());
            try (ResultSet row = statement.executeQuery()) {
                List<LotLocationRecord> lots = new ArrayList<>();
                while (row.next()) {
                    lots.add(readLot(row));
                }
                return Result.success(List.copyOf(lots));
            }
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    private Result<TransactionExecution, TransactionErrorCode> execute(
            TransactionRequest request,
            String expectedOperation,
            AuditSubjectType subjectType,
            UUID subjectId,
            Mutation mutation) {
        Objects.requireNonNull(request, "request");
        if (!request.operationType().equals(expectedOperation)) {
            return Result.failure(
                    TransactionErrorCode.TRANSACTION_OPERATION_MISMATCH,
                    "Transaction operation does not match the requested value mutation.");
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                Result<JournalPrepareOutcome, TransactionErrorCode> preparedResult =
                        JdbcTransactionJournalRepository.prepare(connection, request);
                if (!preparedResult.isSuccess()) {
                    connection.rollback();
                    return copyFailure(preparedResult);
                }
                JournalPrepareOutcome prepared = success(preparedResult);
                if (!prepared.newlyPrepared()) {
                    if (prepared.entry().state() == TransactionState.PREPARED) {
                        connection.rollback();
                        return Result.failure(
                                TransactionErrorCode.TRANSACTION_INVALID_STATE,
                                "Prepared transaction requires reconciliation before retry.");
                    }
                    connection.commit();
                    return Result.success(new TransactionExecution(prepared.entry(), true));
                }

                checkpointObserver.reached(Checkpoint.AFTER_PREPARE);
                Result<MutationApplied, TransactionErrorCode> mutationResult =
                        mutation.apply(connection);
                if (!mutationResult.isSuccess()) {
                    connection.rollback();
                    return copyFailure(mutationResult);
                }
                Result<MutationApplied, TransactionErrorCode> auditResult =
                        appendAudit(connection, request, subjectType, subjectId);
                if (!auditResult.isSuccess()) {
                    connection.rollback();
                    return copyFailure(auditResult);
                }
                checkpointObserver.reached(Checkpoint.AFTER_MUTATION);

                Result<JournalTransitionOutcome, TransactionErrorCode> transitionResult =
                        JdbcTransactionJournalRepository.transition(
                                connection, request.transactionId(), TransactionState.COMMITTED);
                if (!transitionResult.isSuccess()) {
                    connection.rollback();
                    return copyFailure(transitionResult);
                }
                TransactionJournalEntry committed = success(transitionResult).entry();
                connection.commit();
                return Result.success(new TransactionExecution(committed, false));
            } catch (SQLException exception) {
                JdbcTransactionJournalRepository.rollbackQuietly(connection);
                return JdbcTransactionJournalRepository.failure(exception);
            } catch (RuntimeException exception) {
                JdbcTransactionJournalRepository.rollbackQuietly(connection);
                throw exception;
            } finally {
                JdbcTransactionJournalRepository.restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    private static Result<MutationApplied, TransactionErrorCode> insertItem(
            Connection connection, TransactionRequest request, NewItemLocation item) {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO item_instance(
                            item_uuid, definition_id, owner_character_id,
                            location_type, location_ref, payload, content_version,
                            version, last_transaction_id, created_at, updated_at
                        )
                        VALUES (
                            ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, 1, ?,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        ON CONFLICT (item_uuid) DO NOTHING
                        """)) {
            statement.setObject(1, item.itemId().value());
            statement.setString(2, item.definitionId().value());
            setNullableCharacter(statement, 3, item.ownerCharacterId());
            bindLocation(statement, 4, item.location());
            statement.setString(6, item.payloadJson());
            statement.setString(7, request.contentVersion());
            statement.setObject(8, request.transactionId().value());
            if (statement.executeUpdate() == 0) {
                return Result.failure(
                        TransactionErrorCode.VALUE_ALREADY_EXISTS,
                        "Item UUID already has an authoritative location.");
            }
            return Result.success(MutationApplied.INSTANCE);
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    private static Result<MutationApplied, TransactionErrorCode> appendAudit(
            Connection connection,
            TransactionRequest request,
            AuditSubjectType subjectType,
            UUID subjectId) {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO audit_log(
                            transaction_id, actor_character_id, action_type,
                            subject_type, subject_id, details, created_at
                        )
                        VALUES (
                            ?, ?, ?, ?, ?,
                            jsonb_build_object(
                                'reservedInputs', CAST(? AS JSONB),
                                'intendedOutputs', CAST(? AS JSONB),
                                'contentVersion', ?
                            ),
                            CURRENT_TIMESTAMP
                        )
                        """)) {
            statement.setObject(1, request.transactionId().value());
            setNullableCharacter(statement, 2, request.characterId());
            statement.setString(3, request.operationType());
            statement.setString(4, subjectType.name());
            statement.setObject(5, subjectId);
            statement.setString(6, request.reservedInputsJson());
            statement.setString(7, request.intendedOutputsJson());
            statement.setString(8, request.contentVersion());
            statement.executeUpdate();
            return Result.success(MutationApplied.INSTANCE);
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    private static Result<MutationApplied, TransactionErrorCode> insertLot(
            Connection connection, TransactionRequest request, NewLotLocation lot) {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        INSERT INTO commodity_lot(
                            lot_uuid, definition_id, variant, quantity, owner_character_id,
                            location_type, location_ref, lineage, content_version,
                            version, last_transaction_id, created_at, updated_at
                        )
                        VALUES (
                            ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?,
                            1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        ON CONFLICT (lot_uuid) DO NOTHING
                        """)) {
            statement.setObject(1, lot.lotId().value());
            statement.setString(2, lot.definitionId().value());
            statement.setString(3, lot.variant());
            statement.setLong(4, lot.quantity());
            setNullableCharacter(statement, 5, lot.ownerCharacterId());
            bindLocation(statement, 6, lot.location());
            statement.setString(8, lot.lineageJson());
            statement.setString(9, request.contentVersion());
            statement.setObject(10, request.transactionId().value());
            if (statement.executeUpdate() == 0) {
                return Result.failure(
                        TransactionErrorCode.VALUE_ALREADY_EXISTS,
                        "Lot UUID already has an authoritative location.");
            }
            return Result.success(MutationApplied.INSTANCE);
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    private static Result<MutationApplied, TransactionErrorCode> updateItemLocation(
            Connection connection, TransactionId transactionId, ItemLocationMove move) {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        UPDATE item_instance
                        SET owner_character_id = ?,
                            location_type = ?,
                            location_ref = ?,
                            version = version + 1,
                            last_transaction_id = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE item_uuid = ?
                          AND version = ?
                          AND owner_character_id IS NOT DISTINCT FROM ?
                          AND location_type = ?
                          AND location_ref IS NOT DISTINCT FROM ?
                        """)) {
            setNullableCharacter(statement, 1, move.destinationOwnerCharacterId());
            bindLocation(statement, 2, move.destinationLocation());
            statement.setObject(4, transactionId.value());
            statement.setObject(5, move.itemId().value());
            statement.setLong(6, move.expectedVersion());
            setNullableCharacter(statement, 7, move.expectedOwnerCharacterId());
            bindLocation(statement, 8, move.expectedLocation());
            if (statement.executeUpdate() == 1) {
                return Result.success(MutationApplied.INSTANCE);
            }
            return classifyItemCasFailure(connection, move);
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    private static Result<MutationApplied, TransactionErrorCode> updateLotLocation(
            Connection connection, TransactionId transactionId, LotLocationMove move) {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        UPDATE commodity_lot
                        SET owner_character_id = ?,
                            location_type = ?,
                            location_ref = ?,
                            version = version + 1,
                            last_transaction_id = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE lot_uuid = ?
                          AND version = ?
                          AND owner_character_id IS NOT DISTINCT FROM ?
                          AND location_type = ?
                          AND location_ref IS NOT DISTINCT FROM ?
                        """)) {
            setNullableCharacter(statement, 1, move.destinationOwnerCharacterId());
            bindLocation(statement, 2, move.destinationLocation());
            statement.setObject(4, transactionId.value());
            statement.setObject(5, move.lotId().value());
            statement.setLong(6, move.expectedVersion());
            setNullableCharacter(statement, 7, move.expectedOwnerCharacterId());
            bindLocation(statement, 8, move.expectedLocation());
            if (statement.executeUpdate() == 1) {
                return Result.success(MutationApplied.INSTANCE);
            }
            return classifyLotCasFailure(connection, move);
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    private static Result<MutationApplied, TransactionErrorCode> updateLotQuantity(
            Connection connection,
            TransactionId transactionId,
            LotQuantityConsumption consumption) {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        UPDATE commodity_lot
                        SET quantity = quantity - ?,
                            location_type = CASE
                                WHEN quantity = ? THEN 'DESTROYED'
                                ELSE location_type
                            END,
                            location_ref = CASE
                                WHEN quantity = ? THEN ?
                                ELSE location_ref
                            END,
                            version = version + 1,
                            last_transaction_id = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE lot_uuid = ?
                          AND version = ?
                          AND owner_character_id IS NOT DISTINCT FROM ?
                          AND location_type = ?
                          AND location_ref IS NOT DISTINCT FROM ?
                          AND quantity >= ?
                        """)) {
            statement.setLong(1, consumption.quantity());
            statement.setLong(2, consumption.quantity());
            statement.setLong(3, consumption.quantity());
            statement.setString(4, "transaction:" + transactionId.value());
            statement.setObject(5, transactionId.value());
            statement.setObject(6, consumption.lotId().value());
            statement.setLong(7, consumption.expectedVersion());
            setNullableCharacter(statement, 8, consumption.expectedOwnerCharacterId());
            bindLocation(statement, 9, consumption.expectedLocation());
            statement.setLong(11, consumption.quantity());
            if (statement.executeUpdate() == 1) {
                return Result.success(MutationApplied.INSTANCE);
            }
            return classifyLotConsumptionFailure(connection, consumption);
        } catch (SQLException exception) {
            return JdbcTransactionJournalRepository.failure(exception);
        }
    }

    private static Result<MutationApplied, TransactionErrorCode> classifyItemCasFailure(
            Connection connection, ItemLocationMove move) throws SQLException {
        Optional<ItemLocationRecord> current = findItem(connection, move.itemId());
        if (current.isEmpty()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_NOT_FOUND, "Item location does not exist.");
        }
        if (current.orElseThrow().version() != move.expectedVersion()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_STALE_VERSION,
                    "Item version changed; reload before retry.");
        }
        return Result.failure(
                TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                "Item owner or location no longer matches the expected source.");
    }

    private static Result<MutationApplied, TransactionErrorCode> classifyLotCasFailure(
            Connection connection, LotLocationMove move) throws SQLException {
        Optional<LotLocationRecord> current = findLot(connection, move.lotId());
        if (current.isEmpty()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_NOT_FOUND, "Lot location does not exist.");
        }
        if (current.orElseThrow().version() != move.expectedVersion()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_STALE_VERSION,
                    "Lot version changed; reload before retry.");
        }
        return Result.failure(
                TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                "Lot owner or location no longer matches the expected source.");
    }

    private static Result<MutationApplied, TransactionErrorCode> classifyLotConsumptionFailure(
            Connection connection, LotQuantityConsumption consumption) throws SQLException {
        Optional<LotLocationRecord> current = findLot(connection, consumption.lotId());
        if (current.isEmpty()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_NOT_FOUND, "Lot location does not exist.");
        }
        LotLocationRecord record = current.orElseThrow();
        if (record.version() != consumption.expectedVersion()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_STALE_VERSION,
                    "Lot version changed; reload before retry.");
        }
        if (!record.ownerCharacterId().equals(consumption.expectedOwnerCharacterId())
                || !record.location().equals(consumption.expectedLocation())) {
            return Result.failure(
                    TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                    "Lot owner or location no longer matches the expected source.");
        }
        if (record.quantity() < consumption.quantity()) {
            return Result.failure(
                    TransactionErrorCode.VALUE_INSUFFICIENT_QUANTITY,
                    "Lot does not contain the requested quantity.");
        }
        return Result.failure(
                TransactionErrorCode.VALUE_EXPECTATION_MISMATCH,
                "Lot could not be consumed under the declared expectations.");
    }

    private static Optional<ItemLocationRecord> findItem(Connection connection, ItemId itemId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT " + ITEM_COLUMNS + " FROM item_instance WHERE item_uuid = ?")) {
            statement.setObject(1, itemId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readItem(row)) : Optional.empty();
            }
        }
    }

    private static Optional<LotLocationRecord> findLot(Connection connection, LotId lotId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT " + LOT_COLUMNS + " FROM commodity_lot WHERE lot_uuid = ?")) {
            statement.setObject(1, lotId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readLot(row)) : Optional.empty();
            }
        }
    }

    private static ItemLocationRecord readItem(ResultSet row) throws SQLException {
        return new ItemLocationRecord(
                new ItemId(row.getObject("item_uuid", UUID.class)),
                DefinitionId.of(row.getString("definition_id")),
                optionalCharacter(row, "owner_character_id"),
                readLocation(row),
                row.getString("payload"),
                row.getString("content_version"),
                row.getLong("version"),
                new TransactionId(row.getObject("last_transaction_id", UUID.class)),
                instant(row, "created_at"),
                instant(row, "updated_at"));
    }

    private static LotLocationRecord readLot(ResultSet row) throws SQLException {
        return new LotLocationRecord(
                new LotId(row.getObject("lot_uuid", UUID.class)),
                DefinitionId.of(row.getString("definition_id")),
                row.getString("variant"),
                row.getLong("quantity"),
                optionalCharacter(row, "owner_character_id"),
                readLocation(row),
                row.getString("lineage"),
                row.getString("content_version"),
                row.getLong("version"),
                new TransactionId(row.getObject("last_transaction_id", UUID.class)),
                instant(row, "created_at"),
                instant(row, "updated_at"));
    }

    private static ValueLocation readLocation(ResultSet row) throws SQLException {
        return new ValueLocation(
                ValueLocationType.valueOf(row.getString("location_type")),
                Optional.ofNullable(row.getString("location_ref")));
    }

    private static Optional<CharacterId> optionalCharacter(ResultSet row, String column)
            throws SQLException {
        return Optional.ofNullable(row.getObject(column, UUID.class)).map(CharacterId::new);
    }

    private static java.time.Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static void bindLocation(
            PreparedStatement statement, int firstIndex, ValueLocation location)
            throws SQLException {
        statement.setString(firstIndex, location.type().name());
        if (location.reference().isPresent()) {
            statement.setString(firstIndex + 1, location.reference().orElseThrow());
        } else {
            statement.setNull(firstIndex + 1, Types.VARCHAR);
        }
    }

    private static void setNullableCharacter(
            PreparedStatement statement, int index, Optional<CharacterId> characterId)
            throws SQLException {
        if (characterId.isPresent()) {
            statement.setObject(index, characterId.orElseThrow().value());
        } else {
            statement.setNull(index, Types.OTHER);
        }
    }

    private static <T> T success(Result<T, TransactionErrorCode> result) {
        return ((Result.Success<T, TransactionErrorCode>) result).value();
    }

    private static <T, U> Result<U, TransactionErrorCode> copyFailure(
            Result<T, TransactionErrorCode> result) {
        Result.Failure<T, TransactionErrorCode> failure =
                (Result.Failure<T, TransactionErrorCode>) result;
        return Result.failure(failure.error(), failure.detail());
    }

    enum Checkpoint {
        AFTER_PREPARE,
        AFTER_MUTATION
    }

    @FunctionalInterface
    interface CheckpointObserver {
        void reached(Checkpoint checkpoint);
    }

    @FunctionalInterface
    private interface Mutation {
        Result<MutationApplied, TransactionErrorCode> apply(Connection connection);
    }

    private enum MutationApplied {
        INSTANCE
    }
}
