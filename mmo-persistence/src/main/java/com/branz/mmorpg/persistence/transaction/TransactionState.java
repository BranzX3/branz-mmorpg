package com.branz.mmorpg.persistence.transaction;

public enum TransactionState {
    PREPARED,
    COMMITTED,
    ROLLED_BACK,
    QUARANTINED
}
