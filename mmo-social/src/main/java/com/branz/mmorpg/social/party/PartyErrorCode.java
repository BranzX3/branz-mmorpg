package com.branz.mmorpg.social.party;

import com.branz.mmorpg.api.result.ErrorCode;

public enum PartyErrorCode implements ErrorCode {
    PARTY_DISBANDED("party.disbanded"),
    NOT_LEADER("party.not_leader"),
    MEMBER_NOT_FOUND("party.member_not_found"),
    ALREADY_MEMBER("party.already_member"),
    PARTY_FULL("party.full"),
    INVITATION_NOT_FOUND("party.invitation_not_found"),
    INVITATION_EXPIRED("party.invitation_expired"),
    READY_CHECK_ACTIVE("party.ready_check_active"),
    READY_CHECK_NOT_FOUND("party.ready_check_not_found"),
    READY_CHECK_EXPIRED("party.ready_check_expired"),
    OPERATION_ID_REUSED("party.operation_id_reused");

    private final String code;

    PartyErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
