package com.branz.mmorpg.api.gathering;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.event.DomainEvent;
import com.branz.mmorpg.api.operation.OperationId;
import java.time.Instant;
import java.util.UUID;

/** Durable depleted-to-available transition observed by the gathering service. */
public record GatheringNodeRespawned(
        UUID eventId, Instant occurredAt, OperationId operationId,
        UUID nodeInstanceId, ContentId definitionId,
        long reservationSequence) implements DomainEvent {
}
