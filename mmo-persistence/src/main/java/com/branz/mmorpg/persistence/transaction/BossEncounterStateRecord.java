package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.identity.TransactionId;
import java.time.Instant;
import java.util.Objects;

public record BossEncounterStateRecord(
        EncounterId encounterId,
        DefinitionId definitionId,
        String phase,
        String payloadJson,
        String contentVersion,
        long version,
        TransactionId lastTransactionId,
        Instant createdAt,
        Instant updatedAt) {
    public BossEncounterStateRecord {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(definitionId, "definitionId");
        phase = requireText(phase, "phase");
        payloadJson = requireText(payloadJson, "payloadJson");
        contentVersion = requireText(contentVersion, "contentVersion");
        Objects.requireNonNull(lastTransactionId, "lastTransactionId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
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
