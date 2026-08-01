package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.ItemId;
import java.util.Objects;
import java.util.UUID;

record LiveResourceNodeReservation(
        UUID reservationId,
        CharacterId actor,
        ItemId toolItemId,
        long commitAtTick,
        UUID commitOperationId) {
    LiveResourceNodeReservation {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(toolItemId, "toolItemId");
        if (commitAtTick < 0) {
            throw new IllegalArgumentException("commitAtTick must not be negative");
        }
        Objects.requireNonNull(commitOperationId, "commitOperationId");
    }
}
