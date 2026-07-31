package com.branz.mmorpg.combat.input;

import com.branz.mmorpg.api.result.ErrorCode;

public enum InputRejectionCode implements ErrorCode {
    DUPLICATE_OBSERVATION,
    STALE_SEQUENCE,
    ACTION_LOCKED,
    BUFFER_OCCUPIED,
    BUFFER_EXPIRED;

    @Override
    public String code() {
        return name();
    }
}
