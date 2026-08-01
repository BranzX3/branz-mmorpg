package com.branz.mmorpg.persistence.transaction;

import java.util.Objects;

public record PersonalRewardGrantCommitExecution(
        PersonalRewardGrantRecord record, TransactionExecution transaction) {
    public PersonalRewardGrantCommitExecution {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(transaction, "transaction");
    }
}
