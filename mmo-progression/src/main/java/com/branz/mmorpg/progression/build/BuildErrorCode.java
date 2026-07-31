package com.branz.mmorpg.progression.build;

import com.branz.mmorpg.api.result.ErrorCode;

public enum BuildErrorCode implements ErrorCode {
    BUILD_DEFINITION_INVALID,
    BUILD_UNKNOWN_TECHNIQUE,
    BUILD_UNKNOWN_FORM,
    BUILD_UNKNOWN_ATTUNEMENT,
    BUILD_TECHNIQUE_LIMIT_EXCEEDED,
    BUILD_FAMILY_INCOMPATIBLE,
    BUILD_ATTUNEMENT_CAPACITY_EXCEEDED,
    BUILD_ATTUNEMENT_CONFLICT;

    @Override
    public String code() {
        return name();
    }
}
