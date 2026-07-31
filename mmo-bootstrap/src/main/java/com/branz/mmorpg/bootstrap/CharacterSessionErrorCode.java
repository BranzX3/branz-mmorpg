package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.ErrorCode;

enum CharacterSessionErrorCode implements ErrorCode {
    CHARACTER_LEASE_CONFLICT,
    CHARACTER_PERSISTENCE_UNAVAILABLE,
    CHARACTER_STATE_INVALID,
    CHARACTER_AMMO_UNAVAILABLE,
    CHARACTER_TRANSACTION_REJECTED;

    @Override
    public String code() {
        return name();
    }
}
