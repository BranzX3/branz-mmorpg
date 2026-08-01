package com.branz.mmorpg.lifeskills.node;

import com.branz.mmorpg.api.identity.CharacterId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ResourceNodeReservationRequest(
        CharacterId actor,
        UUID toolItemId,
        Set<String> toolTags,
        int availableToolDurability,
        boolean regionEligible,
        boolean actionAvailable,
        int focusCost,
        UUID reservationId,
        UUID operationId,
        long currentTick,
        Instant now) {
    public ResourceNodeReservationRequest {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(toolItemId, "toolItemId");
        toolTags = Set.copyOf(Objects.requireNonNull(toolTags, "toolTags"));
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(now, "now");
        if (availableToolDurability < 0
                || focusCost < 0
                || focusCost > 5
                || currentTick < 0
                || toolTags.stream().anyMatch(tag -> tag == null || tag.isBlank())) {
            throw new IllegalArgumentException("invalid node reservation request");
        }
    }
}
