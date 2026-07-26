package com.branz.mmorpg.api.gathering;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record GatheringNodeInstance(
        UUID instanceId,
        ContentId definitionId,
        WorldBlockPosition position,
        GatheringNodeState state,
        long reservationSequence,
        Optional<Instant> respawnAt,
        Optional<UUID> reservedBy,
        Optional<Instant> reservedUntil,
        Optional<UUID> lastHarvestedBy,
        Optional<Instant> lastHarvestedAt,
        UUID createdBy,
        Instant createdAt) {

    public GatheringNodeInstance {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(respawnAt, "respawnAt");
        Objects.requireNonNull(reservedBy, "reservedBy");
        Objects.requireNonNull(reservedUntil, "reservedUntil");
        Objects.requireNonNull(lastHarvestedBy, "lastHarvestedBy");
        Objects.requireNonNull(lastHarvestedAt, "lastHarvestedAt");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        if (reservationSequence < 0
                || reservedBy.isPresent() != reservedUntil.isPresent()) {
            throw new IllegalArgumentException("invalid gathering node state");
        }
        if (state == GatheringNodeState.RESERVED && reservedBy.isEmpty()) {
            throw new IllegalArgumentException("reserved node lacks reservation owner");
        }
        if (state != GatheringNodeState.RESERVED && reservedBy.isPresent()) {
            throw new IllegalArgumentException("non-reserved node carries a reservation");
        }
        if (state == GatheringNodeState.DEPLETED && respawnAt.isEmpty()) {
            throw new IllegalArgumentException("depleted node lacks respawn time");
        }
    }

    public static GatheringNodeInstance placed(
            UUID id, ContentId definitionId, WorldBlockPosition position,
            UUID creator, Instant now) {
        return new GatheringNodeInstance(id, definitionId, position,
                GatheringNodeState.AVAILABLE, 0, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), creator, now);
    }
}
