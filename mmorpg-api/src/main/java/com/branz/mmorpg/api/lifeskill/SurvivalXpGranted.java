package com.branz.mmorpg.api.lifeskill;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.event.DomainEvent;
import com.branz.mmorpg.api.operation.OperationId;
import java.time.Instant;
import java.util.UUID;

/** Immutable fact published after one exact-once Survival XP commit. */
public record SurvivalXpGranted(
        UUID eventId, Instant occurredAt, OperationId operationId,
        UUID playerId, ContentId skillId, String source,
        long baseXp, long awardedXp, long resultingTotalXp,
        long contentRevision) implements DomainEvent {
}
