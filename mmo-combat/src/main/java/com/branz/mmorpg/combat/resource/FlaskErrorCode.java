package com.branz.mmorpg.combat.resource;

import com.branz.mmorpg.api.result.ErrorCode;

public enum FlaskErrorCode implements ErrorCode {
    FLASK_CHARGE_UNAVAILABLE,
    FLASK_STOCK_INVALID;

    @Override
    public String code() {
        return name();
    }
}
