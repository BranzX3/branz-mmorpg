package com.branz.mmorpg.api.mastery;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record CombatMasteryNodeUnlocked(UUID eventId, Instant occurredAt, UUID playerId,
                                        ContentId masteryId, ContentId nodeId,
                                        int oldRank, int newRank,
                                        int pointsRemaining) implements DomainEvent {
}
