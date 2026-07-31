package com.branz.mmorpg.api.identity;

import com.branz.mmorpg.api.result.ErrorCode;

public enum IdentifierErrorCode implements ErrorCode {
    IDENTIFIER_BLANK,
    IDENTIFIER_TOO_LONG,
    IDENTIFIER_INVALID_FORMAT;

    @Override
    public String code() {
        return name();
    }
}
