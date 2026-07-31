package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.result.ErrorCode;

public enum ReconciliationErrorCode implements ErrorCode {
    RECONCILIATION_DATABASE_UNAVAILABLE;

    @Override
    public String code() {
        return name();
    }
}
