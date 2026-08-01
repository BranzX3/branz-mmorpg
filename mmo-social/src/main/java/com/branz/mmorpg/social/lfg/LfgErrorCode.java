package com.branz.mmorpg.social.lfg;

import com.branz.mmorpg.api.result.ErrorCode;

public enum LfgErrorCode implements ErrorCode {
    LISTING_CLOSED("lfg.listing_closed"),
    NOT_LEADER("lfg.not_leader"),
    LEADER_CANNOT_APPLY("lfg.leader_cannot_apply"),
    REQUIREMENTS_NOT_MET("lfg.requirements_not_met"),
    REQUEST_ALREADY_EXISTS("lfg.request_already_exists"),
    REQUEST_NOT_FOUND("lfg.request_not_found"),
    APPLICANT_ALREADY_ACCEPTED("lfg.applicant_already_accepted"),
    LISTING_FULL("lfg.listing_full"),
    OPERATION_ID_REUSED("lfg.operation_id_reused");

    private final String code;

    LfgErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
