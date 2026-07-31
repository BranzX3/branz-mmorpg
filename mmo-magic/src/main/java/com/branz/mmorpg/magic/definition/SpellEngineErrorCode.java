package com.branz.mmorpg.magic.definition;

import com.branz.mmorpg.api.result.ErrorCode;

public enum SpellEngineErrorCode implements ErrorCode {
    SPELL_FIELD_INVALID;

    @Override
    public String code() {
        return name();
    }
}
