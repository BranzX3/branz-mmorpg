package com.branz.mmorpg.items.consumable;

import com.branz.mmorpg.api.result.ErrorCode;

public enum ConsumableEffectErrorCode implements ErrorCode {
    RARE_REPLACEMENT_CONFIRMATION_REQUIRED;

    @Override
    public String code() {
        return name();
    }
}
