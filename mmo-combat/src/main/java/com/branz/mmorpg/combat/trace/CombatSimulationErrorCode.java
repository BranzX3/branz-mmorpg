package com.branz.mmorpg.combat.trace;

import com.branz.mmorpg.api.result.ErrorCode;

public enum CombatSimulationErrorCode implements ErrorCode {
    RESOURCE_REJECTED,
    COMMAND_INVALID,
    MOVE_NOT_FOUND,
    TRACE_DIVERGED;

    @Override
    public String code() {
        return name();
    }
}
