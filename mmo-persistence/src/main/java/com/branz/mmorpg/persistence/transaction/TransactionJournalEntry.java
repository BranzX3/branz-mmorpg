package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.SessionId;
import com.branz.mmorpg.api.identity.TransactionId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record TransactionJournalEntry(
        TransactionId transactionId,
        String idempotencyKey,
        Optional<CharacterId> characterId,
        Optional<SessionId> sessionId,
        String operationType,
        TransactionState state,
        String reservedInputsJson,
        String intendedOutputsJson,
        String contentVersion,
        Instant createdAt,
        Instant updatedAt) {
    public TransactionJournalEntry {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(operationType, "operationType");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(reservedInputsJson, "reservedInputsJson");
        Objects.requireNonNull(intendedOutputsJson, "intendedOutputsJson");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }
}
