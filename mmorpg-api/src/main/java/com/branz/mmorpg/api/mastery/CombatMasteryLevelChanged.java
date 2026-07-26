package com.branz.mmorpg.api.mastery;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record CombatMasteryLevelChanged(UUID eventId, Instant occurredAt, UUID playerId,
                                        ContentId masteryId, int oldLevel, int newLevel,
                                        int pointsGranted) implements DomainEvent {
}
