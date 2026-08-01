package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.ErrorCode;

enum LiveResourceNodeErrorCode implements ErrorCode {
    NODE_DATABASE_UNAVAILABLE,
    NODE_STATE_INVALID,
    NODE_UNAVAILABLE,
    NODE_TOOL_INVALID,
    NODE_PROGRESSION_INVALID;

    @Override
    public String code() {
        return name();
    }
}
