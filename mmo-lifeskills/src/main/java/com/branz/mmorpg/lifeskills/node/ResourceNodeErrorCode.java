package com.branz.mmorpg.lifeskills.node;

import com.branz.mmorpg.api.result.ErrorCode;

public enum ResourceNodeErrorCode implements ErrorCode {
    RUNTIME_INVALID("node.runtime_invalid"),
    ADMISSION_REJECTED("node.admission_rejected"),
    TOOL_INVALID("node.tool_invalid"),
    TOOL_DURABILITY_INSUFFICIENT("node.tool_durability_insufficient"),
    NODE_UNAVAILABLE("node.unavailable"),
    RESERVATION_INVALID("node.reservation_invalid"),
    COMMIT_TOO_EARLY("node.commit_too_early"),
    RESERVATION_EXPIRED("node.reservation_expired"),
    OPERATION_ID_REUSED("node.operation_id_reused");

    private final String code;

    ResourceNodeErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
