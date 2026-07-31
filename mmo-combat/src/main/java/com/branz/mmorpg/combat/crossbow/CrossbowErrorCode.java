package com.branz.mmorpg.combat.crossbow;

import com.branz.mmorpg.api.result.ErrorCode;

public enum CrossbowErrorCode implements ErrorCode {
    CROSSBOW_ACTION_LOCKED,
    CROSSBOW_CHECKPOINT_MISMATCH;

    @Override
    public String code() {
        return name();
    }
}
