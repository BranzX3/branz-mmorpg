package com.branz.mmorpg.persistence.transaction;

import java.util.Objects;

public record CharacterExpeditionStateCommitExecution(
        CharacterExpeditionStateRecord record, TransactionExecution transaction) {
    public CharacterExpeditionStateCommitExecution {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(transaction, "transaction");
    }
}
