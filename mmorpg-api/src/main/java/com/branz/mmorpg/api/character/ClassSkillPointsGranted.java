package com.branz.mmorpg.api.character;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ClassSkillPointsGranted(UUID eventId, Instant occurredAt, UUID playerId,
                                      ContentId classId, int amount,
                                      int pointsRemaining) implements DomainEvent {
}
