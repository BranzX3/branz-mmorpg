package com.branz.mmorpg.persistence.transaction;

import java.util.Objects;

public record DownedEncounterStateCommitExecution(
        DownedEncounterStateRecord record, TransactionExecution transaction) {
    public DownedEncounterStateCommitExecution {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(transaction, "transaction");
    }
}
