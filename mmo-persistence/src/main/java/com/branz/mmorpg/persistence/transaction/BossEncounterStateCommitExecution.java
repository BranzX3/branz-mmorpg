package com.branz.mmorpg.persistence.transaction;

import java.util.Objects;

public record BossEncounterStateCommitExecution(
        BossEncounterStateRecord record, TransactionExecution transaction) {
    public BossEncounterStateCommitExecution {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(transaction, "transaction");
    }
}
