package com.branz.mmorpg.persistence.transaction;

public enum DeathPouchState {
    PENDING_DEBIT,
    ACTIVE,
    RECOVERING,
    RECOVERED,
    EXPIRED
}
