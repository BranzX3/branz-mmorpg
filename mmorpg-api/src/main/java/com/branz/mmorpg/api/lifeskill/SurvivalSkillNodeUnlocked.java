package com.branz.mmorpg.api.lifeskill;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.event.DomainEvent;
import com.branz.mmorpg.api.operation.OperationId;
import java.time.Instant;
import java.util.UUID;

/** Immutable mastery-rank purchase fact published after commit. */
public record SurvivalSkillNodeUnlocked(
        UUID eventId, Instant occurredAt, OperationId operationId,
        UUID playerId, ContentId skillId, ContentId nodeId,
        int oldRank, int newRank, int pointsSpent, int pointsRemaining,
        long contentRevision) implements DomainEvent {
}
