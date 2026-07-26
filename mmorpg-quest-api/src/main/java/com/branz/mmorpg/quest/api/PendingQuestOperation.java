package com.branz.mmorpg.quest.api;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PendingQuestOperation(
        String operationId,
        UUID playerId,
        ContentId questId,
        ActionDefinition.Type operationType,
        Map<String, String> payload,
        State state,
        int attempts,
        Instant nextAttemptAt,
        String lastError) {
    public enum State { PENDING, RUNNING, COMPLETE, FAILED }
    public PendingQuestOperation {
        operationId = Objects.requireNonNull(operationId, "operationId").trim();
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(operationType, "operationType");
        payload = Map.copyOf(payload);
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        lastError = lastError == null ? "" : lastError;
        if (operationId.isEmpty() || attempts < 0) {
            throw new IllegalArgumentException("invalid pending operation");
        }
    }
}
