package com.branz.mmorpg.api.gathering;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.operation.OperationId;
import java.time.Instant;
import java.util.UUID;

public record GatheringReservation(
        UUID nodeInstanceId,
        ContentId definitionId,
        UUID playerId,
        long reservationSequence,
        Instant startedAt,
        Instant completesAt,
        OperationId operationId) {
}
