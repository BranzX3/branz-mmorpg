package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.identity.TransactionId;
import java.time.Instant;
import java.util.Objects;

public record DownedEncounterStateRecord(
        EncounterId encounterId,
        int attempt,
        boolean recoverable,
        String payloadJson,
        String contentVersion,
        long version,
        TransactionId lastTransactionId,
        Instant createdAt,
        Instant updatedAt) {
    public DownedEncounterStateRecord {
        Objects.requireNonNull(encounterId, "encounterId");
        payloadJson = requireText(payloadJson, "payloadJson");
        contentVersion = requireText(contentVersion, "contentVersion");
        Objects.requireNonNull(lastTransactionId, "lastTransactionId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (attempt < 1 || version < 1) {
            throw new IllegalArgumentException("attempt and version must be positive");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
