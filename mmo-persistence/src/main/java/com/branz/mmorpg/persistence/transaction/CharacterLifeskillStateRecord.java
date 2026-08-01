package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import java.time.Instant;
import java.util.Objects;

public record CharacterLifeskillStateRecord(
        CharacterId characterId,
        String statePayloadJson,
        String contentVersion,
        long version,
        TransactionId lastTransactionId,
        Instant createdAt,
        Instant updatedAt) {
    public CharacterLifeskillStateRecord {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(statePayloadJson, "statePayloadJson");
        Objects.requireNonNull(contentVersion, "contentVersion");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(lastTransactionId, "lastTransactionId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
