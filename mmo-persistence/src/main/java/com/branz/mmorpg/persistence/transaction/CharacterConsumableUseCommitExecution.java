package com.branz.mmorpg.persistence.transaction;

import java.util.Objects;

public record CharacterConsumableUseCommitExecution(
        CharacterExpeditionStateRecord state, TransactionExecution transaction) {
    public CharacterConsumableUseCommitExecution {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(transaction, "transaction");
    }
}
