package com.branz.mmorpg.combat.input;

public enum SemanticInput {
    FORCED_INTERRUPT(100, false),
    UI_DANGER_CLOSE(90, false),
    DODGE(80, false),
    DEFENSIVE_RESPONSE(70, false),
    PRIMARY(40, true),
    SECONDARY(40, true),
    SIGNATURE(50, false),
    AUXILIARY(50, false),
    WORLD_INTERACTION(20, false),
    VANILLA_FALLBACK(10, false);

    private final int priority;
    private final boolean bufferable;

    SemanticInput(int priority, boolean bufferable) {
        this.priority = priority;
        this.bufferable = bufferable;
    }

    public int priority(DirectionSnapshot direction) {
        return priority + (direction == DirectionSnapshot.NEUTRAL ? 0 : 1);
    }

    public boolean bufferable() {
        return bufferable;
    }
}
