package com.branz.mmorpg.combat.dodge;

import com.branz.mmorpg.api.result.ErrorCode;

public enum DodgeErrorCode implements ErrorCode {
    ALREADY_DODGING,
    NO_STAMINA,
    NEUTRAL_DIRECTION;

    @Override
    public String code() {
        return name();
    }
}
