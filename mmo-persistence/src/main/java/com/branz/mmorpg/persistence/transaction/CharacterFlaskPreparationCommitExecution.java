package com.branz.mmorpg.persistence.transaction;

import java.util.Objects;

public record CharacterFlaskPreparationCommitExecution(
        CharacterExpeditionStateRecord record,
        long infusionStockConsumed,
        TransactionExecution transaction) {
    public CharacterFlaskPreparationCommitExecution {
        Objects.requireNonNull(record, "record");
        if (infusionStockConsumed < 0) {
            throw new IllegalArgumentException("infusionStockConsumed must not be negative");
        }
        Objects.requireNonNull(transaction, "transaction");
    }
}
