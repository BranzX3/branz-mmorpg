package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import java.time.Instant;
import java.util.Objects;

public record CharacterExpeditionStateRecord(
        CharacterId characterId,
        String payloadJson,
        String contentVersion,
        long version,
        TransactionId lastTransactionId,
        Instant createdAt,
        Instant updatedAt) {
    public CharacterExpeditionStateRecord {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(lastTransactionId, "lastTransactionId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (payloadJson.isBlank() || contentVersion.isBlank() || version < 1) {
            throw new IllegalArgumentException("invalid character expedition state record");
        }
    }
}
