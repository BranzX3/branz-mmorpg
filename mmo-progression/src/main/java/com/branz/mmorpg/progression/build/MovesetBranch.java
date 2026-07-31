package com.branz.mmorpg.progression.build;

/** Logical input branches that a technique may replace or augment. */
public enum MovesetBranch {
    PRIMARY_1,
    PRIMARY_2,
    PRIMARY_3,
    PRIMARY_DIRECTIONAL_FORWARD,
    PRIMARY_DIRECTIONAL_BACK,
    SECONDARY,
    SECONDARY_DIRECTIONAL,
    DODGE_FOLLOWUP,
    SIGNATURE_F,
    UTILITY_Q,
    DEFENSIVE_FOLLOWUP,
    FINISHER;

    boolean countsTowardReplaceableLimit() {
        return this != SIGNATURE_F && this != UTILITY_Q;
    }
}
