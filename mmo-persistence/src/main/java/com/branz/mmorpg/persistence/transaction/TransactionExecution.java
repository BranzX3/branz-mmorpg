package com.branz.mmorpg.persistence.transaction;

import java.util.Objects;

public record TransactionExecution(TransactionJournalEntry journalEntry, boolean replayed) {
    public TransactionExecution {
        Objects.requireNonNull(journalEntry, "journalEntry");
    }
}
