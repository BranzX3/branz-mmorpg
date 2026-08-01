package com.branz.mmorpg.progression.evidence;

/** Stable reason code retained for audit and qualitative feedback. */
public enum EvidenceSuppressionReason {
    NONE,
    DUPLICATE_EVIDENCE,
    MAXIMUM_REACHED,
    TRAINING_DUMMY_FAMILIARITY_COMPLETE,
    INVULNERABLE_TARGET,
    SELF_CREATED_LOOP,
    ZERO_RISK_INTERACTION,
    CHALLENGE_TOO_LOW,
    OUTCOME_NOT_MEANINGFUL
}
