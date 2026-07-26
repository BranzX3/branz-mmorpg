package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.item.StarterKitDelivery;
import com.branz.mmorpg.api.item.StarterKitDeliveryRepository;
import com.branz.mmorpg.api.operation.OperationId;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcStarterKitDeliveryRepository implements StarterKitDeliveryRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> ITEM_MAP = new TypeReference<>() {};
    private final DatabaseManager database;

    public JdbcStarterKitDeliveryRepository(DatabaseManager database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override public Optional<StarterKitDelivery> find(UUID playerId) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT selection_operation_id, starter_plan_id, starter_plan_revision, "
                                + "starter_weapon_id, starter_additional_items, state, created_at, "
                                + "delivered_at FROM mmorpg_starter_kit_delivery WHERE player_uuid = ?")) {
                    select.setBytes(1, JdbcPlayerProfileRepository.toBytes(playerId));
                    try (ResultSet row = select.executeQuery()) {
                        return row.next() ? Optional.of(read(playerId, row)) : Optional.empty();
                    }
                }
            });
        } catch (SQLException exception) {
            throw failure("failed to load starter delivery for " + playerId, exception);
        }
    }

    @Override public boolean markDelivered(UUID playerId, Instant deliveredAt) {
        try {
            return database.inTransaction(connection -> {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE mmorpg_starter_kit_delivery SET state = 'DELIVERED', delivered_at = ? "
                                + "WHERE player_uuid = ? AND state = 'PENDING'")) {
                    update.setTimestamp(1, Timestamp.from(deliveredAt));
                    update.setBytes(2, JdbcPlayerProfileRepository.toBytes(playerId));
                    return update.executeUpdate() == 1;
                }
            });
        } catch (SQLException exception) {
            throw failure("failed to complete starter delivery for " + playerId, exception);
        }
    }

    private static StarterKitDelivery read(UUID playerId, ResultSet row) throws Exception {
        Map<ContentId, Integer> items = new LinkedHashMap<>();
        JSON.readValue(row.getString("starter_additional_items"), ITEM_MAP)
                .forEach((id, amount) -> items.put(ContentId.parse(id), amount));
        Timestamp deliveredAt = row.getTimestamp("delivered_at");
        return new StarterKitDelivery(playerId,
                OperationId.parse(row.getString("selection_operation_id")),
                ContentId.parse(row.getString("starter_plan_id")),
                row.getInt("starter_plan_revision"),
                ContentId.parse(row.getString("starter_weapon_id")), items,
                StarterKitDelivery.State.valueOf(row.getString("state")),
                row.getTimestamp("created_at").toInstant(),
                deliveredAt == null ? null : deliveredAt.toInstant());
    }

    private static MMOException failure(String message, Exception exception) {
        return new MMOException(ErrorCode.STORAGE_FAILURE, message, exception);
    }
}
