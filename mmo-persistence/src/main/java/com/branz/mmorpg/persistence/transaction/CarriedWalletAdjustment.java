package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;
import java.util.UUID;

public record CarriedWalletAdjustment(
        UUID operationId, CharacterId characterId, CarriedWalletOperationKind kind, long amount) {
    public CarriedWalletAdjustment {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(kind, "kind");
        if (amount < 1) {
            throw new IllegalArgumentException("wallet adjustment amount must be positive");
        }
    }
}
