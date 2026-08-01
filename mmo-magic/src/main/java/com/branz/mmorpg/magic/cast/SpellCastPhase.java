package com.branz.mmorpg.magic.cast;

public enum SpellCastPhase {
    WINDUP,
    CHARGING,
    READY,
    CHANNELING,
    RECOVERY,
    COMPLETE,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETE || this == CANCELLED;
    }
}
