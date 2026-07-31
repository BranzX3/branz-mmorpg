package com.branz.mmorpg.combat.weapon;

import com.branz.mmorpg.api.result.ErrorCode;

public enum WeaponTransitionErrorCode implements ErrorCode {
    WEAPON_DISABLED,
    INVALID_TRANSITION;

    @Override
    public String code() {
        return name();
    }
}
