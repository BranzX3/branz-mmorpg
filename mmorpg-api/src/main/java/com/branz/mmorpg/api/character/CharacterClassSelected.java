package com.branz.mmorpg.api.character;

import com.branz.mmorpg.api.event.DomainEvent;
import com.branz.mmorpg.api.operation.OperationId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Published only after the permanent selection transaction commits. */
public record CharacterClassSelected(
        UUID eventId,
        Instant occurredAt,
        UUID playerId,
        CharacterClassId classId,
        OperationId operationId,
        long contentRevision,
        int starterPlanRevision) implements DomainEvent {
    public CharacterClassSelected {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(classId, "classId");
        Objects.requireNonNull(operationId, "operationId");
        if (contentRevision < 1 || starterPlanRevision < 1) {
            throw new IllegalArgumentException("event revisions must be positive");
        }
    }
}
