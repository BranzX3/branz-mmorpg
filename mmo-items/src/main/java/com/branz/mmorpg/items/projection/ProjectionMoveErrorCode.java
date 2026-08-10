package com.branz.mmorpg.items.projection;

import com.branz.mmorpg.api.result.ErrorCode;

public enum ProjectionMoveErrorCode implements ErrorCode {
    PROJECTION_MOVE_INVALID,
    PROJECTION_MOVE_DUPLICATE,
    PROJECTION_MOVE_UNKNOWN,
    PROJECTION_MOVE_MISSING,
    PROJECTION_MOVE_PROTECTED_SLOT,
    PROJECTION_MOVE_STACKABLE_UNSUPPORTED,
    PROJECTION_MOVE_PERMUTATION_UNSUPPORTED;

    @Override
    public String code() {
        return name();
    }
}
