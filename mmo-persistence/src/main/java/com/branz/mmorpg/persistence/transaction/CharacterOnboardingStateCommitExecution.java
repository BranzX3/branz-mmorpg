package com.branz.mmorpg.persistence.transaction;

import java.util.Objects;

public record CharacterOnboardingStateCommitExecution(
        CharacterOnboardingStateRecord record, TransactionExecution transaction) {
    public CharacterOnboardingStateCommitExecution {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(transaction, "transaction");
    }
}
