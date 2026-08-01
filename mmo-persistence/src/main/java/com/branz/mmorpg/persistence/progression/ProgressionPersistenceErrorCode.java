package com.branz.mmorpg.persistence.progression;

import com.branz.mmorpg.api.result.ErrorCode;

public enum ProgressionPersistenceErrorCode implements ErrorCode {
    PROGRESSION_DATABASE_UNAVAILABLE,
    PROGRESSION_BATCH_INVALID,
    PROGRESSION_EVIDENCE_ID_CONFLICT,
    PROGRESSION_STATE_INVALID;

    @Override
    public String code() {
        return name();
    }
}
