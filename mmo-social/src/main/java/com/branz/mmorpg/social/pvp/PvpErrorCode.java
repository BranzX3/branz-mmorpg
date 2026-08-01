package com.branz.mmorpg.social.pvp;

import com.branz.mmorpg.api.result.ErrorCode;

public enum PvpErrorCode implements ErrorCode {
    ADMISSION_REJECTED("pvp.admission_rejected"),
    PARTICIPANT_INVALID("pvp.participant_invalid"),
    TEAM_INVALID("pvp.team_invalid"),
    MATCH_INVALID_STATE("pvp.match_invalid_state"),
    CHALLENGE_EXPIRED("pvp.challenge_expired"),
    HOSTILE_NOT_ALLOWED("pvp.hostile_not_allowed"),
    OPERATION_ID_REUSED("pvp.operation_id_reused");

    private final String code;

    PvpErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
