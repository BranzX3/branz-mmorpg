package com.branz.mmorpg.api.stat;

import com.branz.mmorpg.api.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ModifierRemoved(UUID eventId, Instant occurredAt, UUID playerId,
                              AttributeModifier modifier) implements DomainEvent {
    public ModifierRemoved {
        Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(playerId); Objects.requireNonNull(modifier);
    }
}
