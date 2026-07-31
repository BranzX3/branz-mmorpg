package com.branz.mmorpg.combat.cc;

public enum CcSeverity {
    FLINCH,
    STAGGER,
    HEAVY_STAGGER,
    KNOCKBACK,
    KNOCKDOWN,
    LAUNCH,
    GRAB;

    public boolean strongerThan(CcSeverity other) {
        return ordinal() > other.ordinal();
    }

    public boolean hard() {
        return this != FLINCH;
    }
}
