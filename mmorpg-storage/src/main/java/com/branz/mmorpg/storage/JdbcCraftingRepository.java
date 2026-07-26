package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.crafting.CraftFinalizeCommit;
import com.branz.mmorpg.api.crafting.CraftJob;
import com.branz.mmorpg.api.crafting.CraftPrepareCommit;
import com.branz.mmorpg.api.crafting.CraftingRepository;
import com.branz.mmorpg.api.crafting.ProfessionSnapshot;
import com.branz.mmorpg.api.crafting.RecipeDefinition;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.operation.OperationId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public final class JdbcCraftingRepository implements CraftingRepository {
    private final DatabaseManager database;

    public JdbcCraftingRepository(DatabaseManager database) {
        this.database = java.util.Objects.requireNonNull(database, "database");
    }

    @Override
    public long allocateSequence(UUID playerId, ContentId recipeId) {
        try {
            return database.inTransaction(connection -> {
                JdbcPlayerProfileRepository.lockPlayer(connection, playerId);
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT next_sequence FROM mmorpg_craft_sequence "
                                + "WHERE player_uuid=? AND recipe_id=? FOR UPDATE")) {
                    select.setBytes(1, bytes(playerId));
                    select.setString(2, recipeId.toString());
                    try (ResultSet row = select.executeQuery()) {
                        if (row.next()) {
                            long sequence = row.getLong(1);
                            try (PreparedStatement update = connection.prepareStatement(
                                    "UPDATE mmorpg_craft_sequence SET next_sequence=? "
                                            + "WHERE player_uuid=? AND recipe_id=?")) {
                                update.setLong(1, Math.addExact(sequence, 1));
                                update.setBytes(2, bytes(playerId));
                                update.setString(3, recipeId.toString());
                                update.executeUpdate();
                            }
                            return sequence;
                        }
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO mmorpg_craft_sequence "
                                + "(player_uuid, recipe_id, next_sequence) VALUES (?, ?, 2)")) {
                    insert.setBytes(1, bytes(playerId));
                    insert.setString(2, recipeId.toString());
                    insert.executeUpdate();
                }
                return 1L;
            });
        } catch (SQLException failure) {
            throw storage("failed to allocate craft sequence", failure);
        }
    }

    @Override
    public ProfessionSnapshot profession(UUID playerId, ContentId professionId) {
        try {
            return database.inTransaction(connection ->
                    loadProfession(connection, playerId, professionId, false));
        } catch (SQLException failure) {
            throw storage("failed to load profession", failure);
        }
    }

    @Override
    public Optional<CraftJob> job(OperationId operationId) {
        try {
            return database.inTransaction(connection ->
                    Optional.ofNullable(loadJob(connection, operationId, false)));
        } catch (SQLException failure) {
            throw storage("failed to load craft job", failure);
        }
    }

    @Override
    public Optional<CraftJob> activeJob(UUID playerId) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT operation_id FROM mmorpg_craft_job WHERE player_uuid=? "
                                + "AND job_status IN ('PENDING_PAYMENT','IN_PROGRESS') "
                                + "ORDER BY created_at LIMIT 1")) {
                    select.setBytes(1, bytes(playerId));
                    try (ResultSet row = select.executeQuery()) {
                        if (!row.next()) return Optional.empty();
                        return Optional.of(requireJob(connection,
                                OperationId.parse(row.getString(1)), false));
                    }
                }
            });
        } catch (SQLException failure) {
            throw storage("failed to load active craft", failure);
        }
    }

    @Override
    public CraftPrepareCommit prepare(
            UUID playerId, RecipeDefinition recipe, long contentRevision,
            Map<ContentId, Long> escrow, OperationId operationId,
            Instant now, UnaryOperator<InventorySnapshot> consumeInputs) {
        requireOwner(playerId, operationId);
        try {
            return database.inTransaction(connection -> {
                JdbcPlayerProfileRepository.lockPlayer(connection, playerId);
                CraftJob existing = loadJob(connection, operationId, true);
                JdbcInventoryRepository.ensureInventory(connection, playerId);
                InventorySnapshot before =
                        JdbcInventoryRepository.loadSnapshot(connection, playerId, true);
                if (existing != null) {
                    return new CraftPrepareCommit(false, existing, before, before);
                }
                try (PreparedStatement active = connection.prepareStatement(
                        "SELECT operation_id FROM mmorpg_craft_job WHERE player_uuid=? "
                                + "AND job_status IN ('PENDING_PAYMENT','IN_PROGRESS') "
                                + "LIMIT 1 FOR UPDATE")) {
                    active.setBytes(1, bytes(playerId));
                    try (ResultSet row = active.executeQuery()) {
                        if (row.next()) {
                            throw new IllegalStateException(
                                    "player already has an active craft " + row.getString(1));
                        }
                    }
                }
                InventorySnapshot after = java.util.Objects.requireNonNull(
                        consumeInputs.apply(before), "consumeInputs returned null");
                CraftJob job = new CraftJob(
                        operationId, playerId, recipe.id(), contentRevision,
                        CraftJob.Status.PENDING_PAYMENT, escrow, recipe.coinFee(),
                        recipe.durationMillis(), recipe.output().itemId(),
                        recipe.output().quantity(), recipe.output().binding(),
                        recipe.output().qualityPolicy(), recipe.professionId(),
                        recipe.professionXp(), recipe.trivialAfterLevel(),
                        Optional.empty(), Optional.empty(), now, now);
                insertJob(connection, job);
                insertEscrow(connection, job);
                JdbcInventoryRepository.replaceSnapshot(connection, after);
                audit(connection, playerId, "craft_prepared", operationId.value());
                return new CraftPrepareCommit(true, job, before, after);
            });
        } catch (SQLException failure) {
            throw storage("failed to prepare craft " + operationId, failure);
        }
    }

    @Override
    public CraftJob markPaymentSettled(
            OperationId operationId, Instant readyAt, Instant now) {
        try {
            return database.inTransaction(connection -> {
                CraftJob job = requireJob(connection, operationId, true);
                if (job.status() != CraftJob.Status.PENDING_PAYMENT) return job;
                CraftJob started = copy(job, CraftJob.Status.IN_PROGRESS,
                        Optional.of(readyAt), Optional.empty(), now);
                updateJob(connection, started);
                audit(connection, job.playerId(), "craft_payment_settled", operationId.value());
                return started;
            });
        } catch (SQLException failure) {
            throw storage("failed to settle craft payment " + operationId, failure);
        }
    }

    @Override
    public CraftJob cancel(
            OperationId operationId, String reason, Instant now,
            UnaryOperator<InventorySnapshot> refundInputs) {
        try {
            return database.inTransaction(connection -> {
                CraftJob job = requireJob(connection, operationId, false);
                JdbcPlayerProfileRepository.lockPlayer(connection, job.playerId());
                job = requireJob(connection, operationId, true);
                if (job.status() == CraftJob.Status.CANCELLED
                        || job.status() == CraftJob.Status.COMPLETE) return job;
                if (job.status() != CraftJob.Status.PENDING_PAYMENT) {
                    throw new IllegalStateException("paid craft cannot be cancelled");
                }
                JdbcInventoryRepository.ensureInventory(connection, job.playerId());
                InventorySnapshot before = JdbcInventoryRepository.loadSnapshot(
                        connection, job.playerId(), true);
                InventorySnapshot after = refundInputs.apply(before);
                JdbcInventoryRepository.replaceSnapshot(connection, after);
                CraftJob cancelled = copy(job, CraftJob.Status.CANCELLED,
                        Optional.empty(), Optional.ofNullable(reason), now);
                updateJob(connection, cancelled);
                audit(connection, job.playerId(), "craft_cancelled", operationId.value());
                return cancelled;
            });
        } catch (SQLException failure) {
            throw storage("failed to cancel craft " + operationId, failure);
        }
    }

    @Override
    public CraftFinalizeCommit finalizeCraft(
            OperationId operationId, Optional<ContentId> professionId, Instant now,
            UnaryOperator<InventorySnapshot> deliverOutput,
            UnaryOperator<ProfessionSnapshot> professionMutation) {
        try {
            return database.inTransaction(connection -> {
                CraftJob initial = requireJob(connection, operationId, false);
                JdbcPlayerProfileRepository.lockPlayer(connection, initial.playerId());
                CraftJob job = requireJob(connection, operationId, true);
                JdbcInventoryRepository.ensureInventory(connection, job.playerId());
                InventorySnapshot inventoryBefore = JdbcInventoryRepository.loadSnapshot(
                        connection, job.playerId(), true);
                Optional<ProfessionSnapshot> professionBefore = professionId.map(id -> {
                    try {
                        return loadProfession(connection, job.playerId(), id, true);
                    } catch (SQLException failure) {
                        throw new SqlRuntimeException(failure);
                    }
                });
                if (job.status() == CraftJob.Status.COMPLETE) {
                    return new CraftFinalizeCommit(false, job,
                            inventoryBefore, inventoryBefore,
                            professionBefore, professionBefore);
                }
                if (job.status() != CraftJob.Status.IN_PROGRESS
                        || job.readyAt().orElseThrow().isAfter(now)) {
                    throw new IllegalStateException("craft is not ready");
                }
                InventorySnapshot inventoryAfter =
                        java.util.Objects.requireNonNull(deliverOutput.apply(inventoryBefore));
                Optional<ProfessionSnapshot> professionAfter = professionBefore.map(before ->
                        java.util.Objects.requireNonNull(professionMutation.apply(before)));
                JdbcInventoryRepository.replaceSnapshot(connection, inventoryAfter);
                if (professionAfter.isPresent()) {
                    writeProfession(connection, job.playerId(), professionAfter.get());
                }
                CraftJob complete = copy(job, CraftJob.Status.COMPLETE,
                        job.readyAt(), Optional.empty(), now);
                updateJob(connection, complete);
                audit(connection, job.playerId(), "craft_completed", operationId.value());
                return new CraftFinalizeCommit(true, complete,
                        inventoryBefore, inventoryAfter, professionBefore, professionAfter);
            });
        } catch (SqlRuntimeException wrapped) {
            throw storage("failed to finalize craft " + operationId, wrapped.getCause());
        } catch (SQLException failure) {
            throw storage("failed to finalize craft " + operationId, failure);
        }
    }

    private static CraftJob loadJob(
            Connection connection, OperationId operationId, boolean lock) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT * FROM mmorpg_craft_job WHERE operation_id = ?"
                        + (lock ? " FOR UPDATE" : ""))) {
            select.setString(1, operationId.value());
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) return null;
                return readJob(connection, row, operationId);
            }
        }
    }

    private static CraftJob requireJob(
            Connection connection, OperationId operationId, boolean lock) throws SQLException {
        CraftJob job = loadJob(connection, operationId, lock);
        if (job == null) throw new IllegalArgumentException("unknown craft " + operationId);
        return job;
    }

    private static CraftJob readJob(
            Connection connection, ResultSet row, OperationId operationId) throws SQLException {
        String profession = row.getString("profession_id");
        Timestamp ready = row.getTimestamp("ready_at");
        String failure = row.getString("failure_reason");
        return new CraftJob(
                operationId, uuid(row.getBytes("player_uuid")),
                ContentId.parse(row.getString("recipe_id")),
                row.getLong("content_revision"),
                CraftJob.Status.valueOf(row.getString("job_status")),
                readEscrow(connection, operationId), row.getLong("coin_fee"),
                row.getLong("duration_millis"),
                ContentId.parse(row.getString("output_item_id")),
                row.getLong("output_quantity"),
                RecipeDefinition.Output.Binding.valueOf(row.getString("output_binding")),
                row.getString("quality_policy"),
                profession == null ? Optional.empty()
                        : Optional.of(ContentId.parse(profession)),
                row.getLong("profession_xp"), row.getInt("trivial_after_level"),
                ready == null ? Optional.empty() : Optional.of(ready.toInstant()),
                Optional.ofNullable(failure),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant());
    }

    private static Map<ContentId, Long> readEscrow(
            Connection connection, OperationId operationId) throws SQLException {
        Map<ContentId, Long> result = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT material_id, quantity FROM mmorpg_craft_escrow "
                        + "WHERE operation_id = ?")) {
            select.setString(1, operationId.value());
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    result.put(ContentId.parse(rows.getString(1)), rows.getLong(2));
                }
            }
        }
        return Map.copyOf(result);
    }

    private static void insertJob(Connection connection, CraftJob job) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO mmorpg_craft_job (operation_id, player_uuid, recipe_id, "
                        + "content_revision, job_status, coin_fee, duration_millis, "
                        + "output_item_id, output_quantity, output_binding, quality_policy, "
                        + "profession_id, profession_xp, trivial_after_level, ready_at, "
                        + "failure_reason, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, job.operationId().value());
            insert.setBytes(2, bytes(job.playerId()));
            insert.setString(3, job.recipeId().toString());
            insert.setLong(4, job.contentRevision());
            insert.setString(5, job.status().name());
            insert.setLong(6, job.coinFee());
            insert.setLong(7, job.durationMillis());
            insert.setString(8, job.outputItemId().toString());
            insert.setLong(9, job.outputQuantity());
            insert.setString(10, job.outputBinding().name());
            insert.setString(11, job.qualityPolicy());
            insert.setString(12, job.professionId().map(ContentId::toString).orElse(null));
            insert.setLong(13, job.professionXp());
            insert.setInt(14, job.trivialAfterLevel());
            setInstant(insert, 15, job.readyAt());
            insert.setString(16, job.failureReason().orElse(null));
            insert.setTimestamp(17, Timestamp.from(job.createdAt()));
            insert.setTimestamp(18, Timestamp.from(job.updatedAt()));
            insert.executeUpdate();
        }
    }

    private static void insertEscrow(Connection connection, CraftJob job) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO mmorpg_craft_escrow "
                        + "(operation_id, material_id, quantity) VALUES (?, ?, ?)")) {
            for (var entry : job.escrowedMaterials().entrySet()) {
                insert.setString(1, job.operationId().value());
                insert.setString(2, entry.getKey().toString());
                insert.setLong(3, entry.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void updateJob(Connection connection, CraftJob job) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE mmorpg_craft_job SET job_status=?, ready_at=?, failure_reason=?, "
                        + "updated_at=? WHERE operation_id=?")) {
            update.setString(1, job.status().name());
            setInstant(update, 2, job.readyAt());
            update.setString(3, job.failureReason().orElse(null));
            update.setTimestamp(4, Timestamp.from(job.updatedAt()));
            update.setString(5, job.operationId().value());
            update.executeUpdate();
        }
    }

    private static ProfessionSnapshot loadProfession(
            Connection connection, UUID playerId, ContentId id, boolean lock)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT level, total_xp, updated_at FROM mmorpg_profession_progress "
                        + "WHERE player_uuid=? AND profession_id=?"
                        + (lock ? " FOR UPDATE" : ""))) {
            select.setBytes(1, bytes(playerId));
            select.setString(2, id.toString());
            try (ResultSet row = select.executeQuery()) {
                if (row.next()) return new ProfessionSnapshot(
                        id, row.getInt(1), row.getLong(2), row.getTimestamp(3).toInstant());
            }
        }
        return ProfessionSnapshot.untrained(id, Instant.EPOCH);
    }

    private static void writeProfession(
            Connection connection, UUID playerId, ProfessionSnapshot profession)
            throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(
                "INSERT INTO mmorpg_profession_progress "
                        + "(player_uuid, profession_id, level, total_xp, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                        + "level=VALUES(level), total_xp=VALUES(total_xp), "
                        + "updated_at=VALUES(updated_at)")) {
            upsert.setBytes(1, bytes(playerId));
            upsert.setString(2, profession.professionId().toString());
            upsert.setInt(3, profession.level());
            upsert.setLong(4, profession.totalXp());
            upsert.setTimestamp(5, Timestamp.from(profession.updatedAt()));
            upsert.executeUpdate();
        }
    }

    private static CraftJob copy(
            CraftJob job, CraftJob.Status status,
            Optional<Instant> readyAt, Optional<String> failure, Instant now) {
        return new CraftJob(job.operationId(), job.playerId(), job.recipeId(),
                job.contentRevision(), status, job.escrowedMaterials(), job.coinFee(),
                job.durationMillis(), job.outputItemId(), job.outputQuantity(),
                job.outputBinding(), job.qualityPolicy(), job.professionId(),
                job.professionXp(), job.trivialAfterLevel(), readyAt, failure,
                job.createdAt(), now);
    }

    private static void audit(
            Connection connection, UUID actor, String action, String subject) throws SQLException {
        try (PreparedStatement audit = connection.prepareStatement(
                "INSERT INTO mmorpg_audit_log (actor_uuid, action, subject) VALUES (?, ?, ?)")) {
            audit.setBytes(1, bytes(actor));
            audit.setString(2, action);
            audit.setString(3, subject);
            audit.executeUpdate();
        }
    }

    private static void setInstant(
            PreparedStatement statement, int index, Optional<Instant> value) throws SQLException {
        if (value.isPresent()) statement.setTimestamp(index, Timestamp.from(value.get()));
        else statement.setNull(index, java.sql.Types.TIMESTAMP);
    }

    private static void requireOwner(UUID playerId, OperationId operationId) {
        if (!playerId.equals(operationId.playerUuid())) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "craft operation belongs to another player");
        }
    }

    private static byte[] bytes(UUID value) {
        return JdbcPlayerProfileRepository.toBytes(value);
    }

    private static UUID uuid(byte[] value) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static MMOException storage(String message, Throwable cause) {
        return new MMOException(ErrorCode.STORAGE_FAILURE, message, cause);
    }

    private static final class SqlRuntimeException extends RuntimeException {
        private SqlRuntimeException(SQLException cause) { super(cause); }
        @Override public synchronized SQLException getCause() {
            return (SQLException) super.getCause();
        }
    }
}
