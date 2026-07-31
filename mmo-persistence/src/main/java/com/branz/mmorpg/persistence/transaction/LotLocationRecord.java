package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.identity.TransactionId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record LotLocationRecord(
        LotId lotId,
        DefinitionId definitionId,
        String variant,
        long quantity,
        Optional<CharacterId> ownerCharacterId,
        ValueLocation location,
        String lineageJson,
        String contentVersion,
        long version,
        TransactionId lastTransactionId,
        Instant createdAt,
        Instant updatedAt) {
    public LotLocationRecord {
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(variant, "variant");
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        Objects.requireNonNull(ownerCharacterId, "ownerCharacterId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(lineageJson, "lineageJson");
        Objects.requireNonNull(contentVersion, "contentVersion");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(lastTransactionId, "lastTransactionId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
