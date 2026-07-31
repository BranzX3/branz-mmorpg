package com.branz.mmorpg.magic.cast;

import com.branz.mmorpg.api.result.ErrorCode;

public enum SpellCastErrorCode implements ErrorCode {
    NO_MANA,
    CATALYST_INCOMPATIBLE,
    ATTUNEMENT_INSUFFICIENT,
    CAST_TYPE_UNSUPPORTED,
    RELEASE_TOO_EARLY,
    CAST_ALREADY_COMMITTED,
    CAST_ALREADY_FINISHED;

    @Override
    public String code() {
        return name();
    }
}
