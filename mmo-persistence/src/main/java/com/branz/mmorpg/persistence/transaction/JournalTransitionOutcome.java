package com.branz.mmorpg.persistence.transaction;

import java.util.Objects;

public record JournalTransitionOutcome(TransactionJournalEntry entry, boolean changed) {
    public JournalTransitionOutcome {
        Objects.requireNonNull(entry, "entry");
    }
}
