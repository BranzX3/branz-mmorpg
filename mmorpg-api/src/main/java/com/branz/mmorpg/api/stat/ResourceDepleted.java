package com.branz.mmorpg.api.stat;

import com.branz.mmorpg.api.event.DomainEvent;
import com.branz.mmorpg.api.skill.ResourceType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ResourceDepleted(UUID eventId, Instant occurredAt, UUID playerId,
                               ResourceType resource, String reason) implements DomainEvent {
    public ResourceDepleted {
        Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(playerId); Objects.requireNonNull(resource);
        reason = reason == null ? "" : reason;
    }
}
