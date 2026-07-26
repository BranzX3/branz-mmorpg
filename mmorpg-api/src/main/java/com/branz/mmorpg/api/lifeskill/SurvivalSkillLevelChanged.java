package com.branz.mmorpg.api.lifeskill;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.event.DomainEvent;
import com.branz.mmorpg.api.operation.OperationId;
import java.time.Instant;
import java.util.UUID;

/** One ordered level crossing caused by a committed Survival XP operation. */
public record SurvivalSkillLevelChanged(
        UUID eventId, Instant occurredAt, OperationId operationId,
        UUID playerId, ContentId skillId, int oldLevel, int newLevel,
        long totalXp, int pointsGranted, long contentRevision) implements DomainEvent {
}
