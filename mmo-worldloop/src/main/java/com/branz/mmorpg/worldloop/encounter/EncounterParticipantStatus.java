package com.branz.mmorpg.worldloop.encounter;

/** Availability used by the authoritative wipe decision. */
public enum EncounterParticipantStatus {
    ACTIVE,
    DEFEATED,
    DISCONNECTED_GRACE,
    OUTSIDE_GRACE
}
