package com.branz.mmorpg.worldloop.encounter;

/** Durable phases for a boss encounter attempt and its terminal reconciliation. */
public enum BossEncounterPhase {
    ACTIVE,
    WIPE_PENDING,
    RESETTING,
    VICTORY_PENDING,
    COMPLETED
}
