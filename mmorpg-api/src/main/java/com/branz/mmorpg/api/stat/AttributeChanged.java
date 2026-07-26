package com.branz.mmorpg.api.stat;

import com.branz.mmorpg.api.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AttributeChanged(UUID eventId, Instant occurredAt, UUID playerId,
                               AttributeType attribute, double before, double after)
        implements DomainEvent {
    public AttributeChanged {
        Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(playerId); Objects.requireNonNull(attribute);
        if (!Double.isFinite(before) || !Double.isFinite(after)) {
            throw new IllegalArgumentException("attribute event values must be finite");
        }
    }
}
