package com.branz.mmorpg.lifeskills.progression;

import com.branz.mmorpg.api.result.ErrorCode;

public enum LifeskillProgressionErrorCode implements ErrorCode {
    EVIDENCE_INVALID("lifeskill.evidence_invalid"),
    RUNTIME_INVALID("lifeskill.runtime_invalid"),
    OPERATION_ID_REUSED("lifeskill.operation_id_reused");

    private final String code;

    LifeskillProgressionErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
