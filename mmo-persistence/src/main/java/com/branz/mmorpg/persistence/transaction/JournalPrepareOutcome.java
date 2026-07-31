package com.branz.mmorpg.persistence.transaction;

import java.util.Objects;

public record JournalPrepareOutcome(TransactionJournalEntry entry, boolean newlyPrepared) {
    public JournalPrepareOutcome {
        Objects.requireNonNull(entry, "entry");
        if (newlyPrepared && entry.state() != TransactionState.PREPARED) {
            throw new IllegalArgumentException("a newly prepared entry must be PREPARED");
        }
    }
}
