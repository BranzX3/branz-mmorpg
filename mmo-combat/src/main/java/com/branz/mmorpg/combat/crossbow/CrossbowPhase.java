package com.branz.mmorpg.combat.crossbow;

/** Server-owned Crossbow reload and ready phases. */
public enum CrossbowPhase {
    UNLOADED,
    COCKING,
    BOLT_PLACED,
    LOCKING,
    LOADED,
    FIRED
}
