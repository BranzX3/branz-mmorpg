package com.branz.mmorpg.combat.damage;

public enum ConditionalAdvantage {
    COUNTER_HIT(1.20),
    BACK_ATTACK(1.15),
    WEAK_POINT(1.25),
    POSTURE_BREAK(1.35);

    private final double multiplier;

    ConditionalAdvantage(double multiplier) {
        this.multiplier = multiplier;
    }

    public double multiplier() {
        return multiplier;
    }
}
