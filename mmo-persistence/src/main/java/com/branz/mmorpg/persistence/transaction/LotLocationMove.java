package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.LotId;
import java.util.Objects;
import java.util.Optional;

public record LotLocationMove(
        LotId lotId,
        long expectedVersion,
        Optional<CharacterId> expectedOwnerCharacterId,
        ValueLocation expectedLocation,
        Optional<CharacterId> destinationOwnerCharacterId,
        ValueLocation destinationLocation) {
    public LotLocationMove {
        Objects.requireNonNull(lotId, "lotId");
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("expectedVersion must be positive");
        }
        Objects.requireNonNull(expectedOwnerCharacterId, "expectedOwnerCharacterId");
        Objects.requireNonNull(expectedLocation, "expectedLocation");
        Objects.requireNonNull(destinationOwnerCharacterId, "destinationOwnerCharacterId");
        Objects.requireNonNull(destinationLocation, "destinationLocation");
    }
}
