package com.branz.mmorpg.api.item;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.operation.OperationId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Durable starter plan created atomically with permanent-class selection. */
public record StarterKitDelivery(
        UUID playerId,
        OperationId selectionOperationId,
        ContentId planId,
        int planRevision,
        ContentId weaponId,
        Map<ContentId, Integer> additionalItems,
        State state,
        Instant createdAt,
        Instant deliveredAt) {
    public enum State { PENDING, DELIVERED }

    public StarterKitDelivery {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(selectionOperationId, "selectionOperationId");
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(weaponId, "weaponId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        additionalItems = Map.copyOf(Objects.requireNonNull(additionalItems, "additionalItems"));
        if (!playerId.equals(selectionOperationId.playerUuid()) || planRevision < 1
                || (state == State.DELIVERED && deliveredAt == null)) {
            throw new IllegalArgumentException("invalid starter-kit delivery");
        }
    }
}
