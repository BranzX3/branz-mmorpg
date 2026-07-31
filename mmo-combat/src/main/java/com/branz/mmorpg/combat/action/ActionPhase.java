package com.branz.mmorpg.combat.action;

public enum ActionPhase {
    WINDUP,
    ACTIVE,
    RECOVERY,
    COMPLETE,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETE || this == CANCELLED;
    }
}
