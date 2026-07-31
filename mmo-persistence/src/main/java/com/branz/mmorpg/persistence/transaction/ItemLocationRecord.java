package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.TransactionId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ItemLocationRecord(
        ItemId itemId,
        DefinitionId definitionId,
        Optional<CharacterId> ownerCharacterId,
        ValueLocation location,
        String payloadJson,
        String contentVersion,
        long version,
        TransactionId lastTransactionId,
        Instant createdAt,
        Instant updatedAt) {
    public ItemLocationRecord {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(ownerCharacterId, "ownerCharacterId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Objects.requireNonNull(contentVersion, "contentVersion");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(lastTransactionId, "lastTransactionId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
