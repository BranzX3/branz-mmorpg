package com.branz.mmorpg.api.character;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ClassSkillNodeUnlocked(UUID eventId, Instant occurredAt, UUID playerId,
                                     ContentId classId, ContentId nodeId,
                                     int oldRank, int newRank,
                                     int pointsRemaining) implements DomainEvent {
}
