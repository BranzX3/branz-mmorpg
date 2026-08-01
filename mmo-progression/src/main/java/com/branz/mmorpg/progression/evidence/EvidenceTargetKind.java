package com.branz.mmorpg.progression.evidence;

/** Server-classified target context. Clients cannot choose this value. */
public enum EvidenceTargetKind {
    MEANINGFUL_ENCOUNTER,
    TRAINING_DUMMY,
    INVULNERABLE_TARGET,
    SELF_CREATED_LOOP,
    ZERO_RISK_INTERACTION
}
