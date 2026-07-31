package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import java.util.Optional;

public interface TransactionJournalRepository {
    Result<JournalPrepareOutcome, TransactionErrorCode> prepare(TransactionRequest request);

    Result<JournalTransitionOutcome, TransactionErrorCode> transition(
            TransactionId transactionId, TransactionState targetState);

    Result<Optional<TransactionJournalEntry>, TransactionErrorCode> find(
            TransactionId transactionId);

    Result<Optional<TransactionJournalEntry>, TransactionErrorCode> findByIdempotencyKey(
            String idempotencyKey);
}
