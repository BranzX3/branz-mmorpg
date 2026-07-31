package com.branz.mmorpg.combat.bow;

public enum BowDrawPhase {
    DRAWING,
    READY_DRAW,
    FULL_DRAW,
    STRAINED,
    RELEASED,
    CANCELLED;

    public boolean terminal() {
        return this == RELEASED || this == CANCELLED;
    }
}
