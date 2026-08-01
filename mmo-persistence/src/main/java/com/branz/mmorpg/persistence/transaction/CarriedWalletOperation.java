package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.TransactionId;
import java.util.Objects;
import java.util.UUID;

public record CarriedWalletOperation(
        UUID operationId,
        CharacterId characterId,
        CarriedWalletOperationKind kind,
        long amount,
        long resultingBalance,
        TransactionId transactionId) {
    public CarriedWalletOperation {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(transactionId, "transactionId");
        if (amount < 1 || resultingBalance < 0) {
            throw new IllegalArgumentException("invalid carried-wallet operation");
        }
    }

    public boolean matches(CarriedWalletAdjustment adjustment) {
        Objects.requireNonNull(adjustment, "adjustment");
        return characterId.equals(adjustment.characterId())
                && kind == adjustment.kind()
                && amount == adjustment.amount();
    }
}
