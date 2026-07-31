package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.LotId;
import java.util.Objects;
import java.util.Optional;

/** Compare-and-set request that consumes part or all of one authoritative commodity lot. */
public record LotQuantityConsumption(
        LotId lotId,
        long expectedVersion,
        Optional<CharacterId> expectedOwnerCharacterId,
        ValueLocation expectedLocation,
        long quantity) {
    public LotQuantityConsumption {
        Objects.requireNonNull(lotId, "lotId");
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("expectedVersion must be positive");
        }
        Objects.requireNonNull(expectedOwnerCharacterId, "expectedOwnerCharacterId");
        Objects.requireNonNull(expectedLocation, "expectedLocation");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
