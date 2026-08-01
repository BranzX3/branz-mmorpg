package com.branz.mmorpg.lifeskills.progression;

import com.branz.mmorpg.api.result.ErrorCode;

public enum LifeFocusErrorCode implements ErrorCode {
    COST_INVALID("lifeskill.focus_cost_invalid"),
    FOCUS_INSUFFICIENT("lifeskill.focus_insufficient"),
    CLOCK_MOVED_BACKWARD("lifeskill.focus_clock_moved_backward"),
    OPERATION_ID_REUSED("lifeskill.focus_operation_id_reused");

    private final String code;

    LifeFocusErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
