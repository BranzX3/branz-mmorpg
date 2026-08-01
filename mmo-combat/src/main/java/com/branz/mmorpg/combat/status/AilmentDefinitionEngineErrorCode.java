package com.branz.mmorpg.combat.status;

import com.branz.mmorpg.api.result.ErrorCode;

public enum AilmentDefinitionEngineErrorCode implements ErrorCode {
    AILMENT_DEFINITION_INVALID;

    @Override
    public String code() {
        return name();
    }
}
