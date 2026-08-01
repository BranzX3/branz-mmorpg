package com.branz.mmorpg.progression.evidence;

/** Server-confirmed encounter outcome used by the evidence resolver. */
public enum EncounterOutcome {
    VICTORY(1.0),
    DEFEAT(0.5),
    RETREAT(0.25),
    ABANDONED(0.0);

    private final double evidenceFactor;

    EncounterOutcome(double evidenceFactor) {
        this.evidenceFactor = evidenceFactor;
    }

    public double evidenceFactor() {
        return evidenceFactor;
    }
}
