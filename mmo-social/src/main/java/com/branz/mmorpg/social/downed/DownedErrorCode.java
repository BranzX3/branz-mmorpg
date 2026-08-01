package com.branz.mmorpg.social.downed;

import com.branz.mmorpg.api.result.ErrorCode;

public enum DownedErrorCode implements ErrorCode {
    INVALID_PARTICIPANTS("downed.invalid_participants"),
    PARTICIPANT_NOT_FOUND("downed.participant_not_found"),
    INVALID_LIFE_STATE("downed.invalid_life_state"),
    REVIVE_TARGET_EXPIRED("downed.revive_target_expired"),
    REVIVE_CHANNEL_BUSY("downed.revive_channel_busy"),
    REVIVE_CHANNEL_NOT_FOUND("downed.revive_channel_not_found"),
    OPERATION_ID_REUSED("downed.operation_id_reused");

    private final String code;

    DownedErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
