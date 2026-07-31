package com.branz.mmorpg.combat.state;

import com.branz.mmorpg.api.result.ErrorCode;

public enum CombatStateErrorCode implements ErrorCode {
    UI_BLOCKED_WHILE_ENGAGED,
    ACTION_LOCKED,
    INVALID_TRANSITION;

    @Override
    public String code() {
        return name();
    }
}
