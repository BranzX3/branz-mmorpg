package com.branz.mmorpg.combat.state;

public enum ActionState {
    IDLE,
    WINDUP,
    ACTIVE,
    RECOVERY,
    CHANNELING,
    STAGGERED,
    KNOCKED_DOWN,
    GRABBED,
    DOWNED,
    DEAD;

    public boolean hardControl() {
        return this == STAGGERED
                || this == KNOCKED_DOWN
                || this == GRABBED
                || this == DOWNED
                || this == DEAD;
    }
}
