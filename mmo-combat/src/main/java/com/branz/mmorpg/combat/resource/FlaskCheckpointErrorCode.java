package com.branz.mmorpg.combat.resource;

import com.branz.mmorpg.api.result.ErrorCode;

public enum FlaskCheckpointErrorCode implements ErrorCode {
    FLASK_CHECKPOINT_MISMATCH,
    FLASK_WIPE_NOT_CONFIRMED;

    @Override
    public String code() {
        return name();
    }
}
