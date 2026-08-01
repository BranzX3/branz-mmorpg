package com.branz.mmorpg.persistence.transaction;

import java.util.Objects;

public record DeathPouchCommitExecution(DeathPouchRecord record, TransactionExecution transaction) {
    public DeathPouchCommitExecution {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(transaction, "transaction");
    }
}
