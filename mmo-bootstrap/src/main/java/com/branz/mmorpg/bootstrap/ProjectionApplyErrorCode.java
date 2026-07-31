package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.ErrorCode;

enum ProjectionApplyErrorCode implements ErrorCode {
    PROJECTION_INVALID_DATABASE_SLOT,
    PROJECTION_DEFINITION_MISSING,
    PROJECTION_CLASS_MISMATCH,
    PROJECTION_NO_SAFE_SPACE;

    @Override
    public String code() {
        return name();
    }
}
