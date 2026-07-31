package com.branz.mmorpg.items.definition;

import com.branz.mmorpg.api.result.ErrorCode;

public enum WeaponLoadoutErrorCode implements ErrorCode {
    MAIN_HAND_NOT_WEAPON,
    OFF_HAND_MUST_BE_EMPTY,
    SHIELD_REQUIRED,
    SHIELD_NOT_COMPATIBLE;

    @Override
    public String code() {
        return name();
    }
}
