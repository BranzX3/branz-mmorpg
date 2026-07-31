package com.branz.mmorpg.combat.guard;

import com.branz.mmorpg.api.result.ErrorCode;

public enum GuardErrorCode implements ErrorCode {
    GUARD_BROKEN,
    ALREADY_GUARDING,
    NOT_GUARDING;

    @Override
    public String code() {
        return name();
    }
}
