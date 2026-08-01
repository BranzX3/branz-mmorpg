package com.branz.mmorpg.worldloop.encounter;

import com.branz.mmorpg.api.result.ErrorCode;

public enum BossEncounterErrorCode implements ErrorCode {
    INVALID_PARTICIPANTS("encounter.invalid_participants"),
    PARTICIPANT_NOT_FOUND("encounter.participant_not_found"),
    INVALID_PHASE("encounter.invalid_phase"),
    INVALID_PARTICIPANT_STATE("encounter.invalid_participant_state"),
    GRACE_EXPIRED("encounter.grace_expired"),
    OPERATION_ID_REUSED("encounter.operation_id_reused"),
    RESET_OPERATION_MISMATCH("encounter.reset_operation_mismatch");

    private final String code;

    BossEncounterErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
