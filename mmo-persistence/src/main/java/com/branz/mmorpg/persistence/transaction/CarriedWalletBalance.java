package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import java.util.Objects;
import java.util.Optional;

public record CarriedWalletBalance(
        CharacterId characterId,
        long balance,
        long version,
        Optional<TransactionId> lastTransactionId) {
    public CarriedWalletBalance {
        Objects.requireNonNull(characterId, "characterId");
        lastTransactionId = Objects.requireNonNull(lastTransactionId, "lastTransactionId");
        if (balance < 0 || version < 0 || (version == 0) != lastTransactionId.isEmpty()) {
            throw new IllegalArgumentException("invalid carried wallet balance/version");
        }
    }

    public static CarriedWalletBalance empty(CharacterId characterId) {
        return new CarriedWalletBalance(characterId, 0, 0, Optional.empty());
    }
}
