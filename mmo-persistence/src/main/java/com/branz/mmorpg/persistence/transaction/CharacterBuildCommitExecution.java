package com.branz.mmorpg.persistence.transaction;

import java.util.Objects;

public record CharacterBuildCommitExecution(
        CharacterBuildRecord record, TransactionExecution transaction) {
    public CharacterBuildCommitExecution {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(transaction, "transaction");
    }
}
