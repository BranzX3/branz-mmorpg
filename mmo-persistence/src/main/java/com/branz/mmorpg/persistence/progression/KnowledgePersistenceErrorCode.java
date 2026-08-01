package com.branz.mmorpg.persistence.progression;

import com.branz.mmorpg.api.result.ErrorCode;

public enum KnowledgePersistenceErrorCode implements ErrorCode {
    TEACHING_REQUEST_INVALID,
    TEACHING_SESSION_ID_CONFLICT,
    RENOWN_DEED_ID_CONFLICT,
    KNOWLEDGE_ALREADY_LEARNED,
    KNOWLEDGE_STATE_INVALID,
    KNOWLEDGE_DATABASE_UNAVAILABLE;

    @Override
    public String code() {
        return name();
    }
}
