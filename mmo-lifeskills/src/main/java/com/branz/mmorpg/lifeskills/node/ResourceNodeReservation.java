package com.branz.mmorpg.lifeskills.node;

import com.branz.mmorpg.api.identity.CharacterId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Frozen pre-commit ownership, tool and deterministic-yield inputs. */
public record ResourceNodeReservation(
        UUID reservationId,
        CharacterId owner,
        UUID toolItemId,
        UUID yieldSeed,
        int durabilityCost,
        int focusCost,
        long commitAtTick,
        Instant reservedAt,
        Instant expiresAt) {
    public ResourceNodeReservation {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(toolItemId, "toolItemId");
        Objects.requireNonNull(yieldSeed, "yieldSeed");
        Objects.requireNonNull(reservedAt, "reservedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (durabilityCost < 1
                || focusCost < 0
                || focusCost > 5
                || commitAtTick < 0
                || !expiresAt.isAfter(reservedAt)) {
            throw new IllegalArgumentException("invalid resource-node reservation");
        }
    }
}
