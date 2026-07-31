package com.branz.mmorpg.combat.move;

import com.branz.mmorpg.api.result.ErrorCode;

public enum MoveEngineErrorCode implements ErrorCode {
    MOVE_FIELD_INVALID,
    MOVE_TIMELINE_INVALID,
    MOVE_COST_INVALID,
    MOVE_HITBOX_INVALID,
    MOVE_WINDOW_INVALID;

    @Override
    public String code() {
        return name();
    }
}
