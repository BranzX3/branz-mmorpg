package com.branz.mmorpg.api.character;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record CharacterClassLevelChanged(UUID eventId, Instant occurredAt, UUID playerId,
                                         ContentId classId, int oldLevel, int newLevel,
                                         long totalXp) implements DomainEvent {
}
