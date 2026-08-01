package com.branz.mmorpg.items.consumable;

public enum ConsumableUsePhase {
    WINDUP,
    COMMITTED,
    RECOVERY,
    COMPLETE,
    CANCELLED_BEFORE_COMMIT,
    INTERRUPTED_AFTER_COMMIT;

    public boolean terminal() {
        return this == COMPLETE
                || this == CANCELLED_BEFORE_COMMIT
                || this == INTERRUPTED_AFTER_COMMIT;
    }
}
