package com.branz.mmorpg.api.gathering;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.event.DomainEvent;
import com.branz.mmorpg.api.operation.OperationId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Node depletion and authoritative yields committed as one transaction. */
public record GatheringNodeHarvested(
        UUID eventId, Instant occurredAt, OperationId operationId,
        UUID nodeInstanceId, ContentId definitionId, UUID playerId,
        Map<ContentId, Long> yields, Instant respawnAt,
        long contentRevision) implements DomainEvent {
    public GatheringNodeHarvested {
        yields = Map.copyOf(yields);
    }
}
