package com.branz.mmorpg.api.stat;

import com.branz.mmorpg.api.event.DomainEvent;
import com.branz.mmorpg.api.skill.ResourceType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ResourceChanged(UUID eventId, Instant occurredAt, UUID playerId,
                              ResourceType resource, double before, double after,
                              double maximum, String reason) implements DomainEvent {
    public ResourceChanged {
        Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(playerId); Objects.requireNonNull(resource);
        reason = reason == null ? "" : reason;
        if (!Double.isFinite(before) || !Double.isFinite(after) || !Double.isFinite(maximum)) {
            throw new IllegalArgumentException("resource event values must be finite");
        }
    }
}
