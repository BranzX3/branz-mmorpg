package com.branz.mmorpg.progression.teaching;

import com.branz.mmorpg.api.result.ErrorCode;

public enum TeachingErrorCode implements ErrorCode {
    INVALID_PARTICIPANT,
    PARTICIPANT_OFFLINE,
    INVALID_TEACHING_TARGET,
    TEACHER_MISSING_KNOWLEDGE,
    TEACHER_NOT_READY,
    STUDENT_NOT_ELIGIBLE,
    WRONG_ACTOR,
    INVALID_PHASE,
    CHALLENGE_ACTION_INVALID,
    SESSION_EXPIRED,
    SESSION_CANCELLED;

    @Override
    public String code() {
        return name();
    }
}
