package com.branz.mmorpg.api.character;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ClassSkillTreeRespecced(UUID eventId, Instant occurredAt, UUID playerId,
                                      ContentId classId, int refundedPoints,
                                      int pointsRemaining, int treeRevision) implements DomainEvent {
}
