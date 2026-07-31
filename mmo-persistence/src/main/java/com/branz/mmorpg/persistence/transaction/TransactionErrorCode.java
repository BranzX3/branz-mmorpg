package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.result.ErrorCode;

public enum TransactionErrorCode implements ErrorCode {
    TRANSACTION_DATABASE_UNAVAILABLE,
    TRANSACTION_INVALID_JSON,
    TRANSACTION_ID_CONFLICT,
    TRANSACTION_IDEMPOTENCY_CONFLICT,
    TRANSACTION_NOT_FOUND,
    TRANSACTION_INVALID_STATE,
    TRANSACTION_OPERATION_MISMATCH,
    VALUE_ALREADY_EXISTS,
    VALUE_NOT_FOUND,
    VALUE_STALE_VERSION,
    VALUE_EXPECTATION_MISMATCH;

    @Override
    public String code() {
        return name();
    }
}
