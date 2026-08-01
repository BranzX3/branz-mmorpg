package com.branz.mmorpg.social.pvp;

/** Snapshot of admission facts that must all be safe before a PvP match can form. */
public record PvpAdmission(
        boolean characterReady,
        boolean engaged,
        boolean safeRegion,
        boolean externalValueTransactionActive) {
    public static PvpAdmission eligible() {
        return new PvpAdmission(true, false, true, false);
    }

    public boolean accepted() {
        return characterReady && !engaged && safeRegion && !externalValueTransactionActive;
    }
}
