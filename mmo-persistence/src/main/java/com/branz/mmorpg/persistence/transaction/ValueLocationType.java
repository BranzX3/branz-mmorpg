package com.branz.mmorpg.persistence.transaction;

/**
 * Foundational locations needed before the full item engine. Additional authoritative locations
 * belong to their owning runtime milestones.
 */
public enum ValueLocationType {
    CHARACTER_INVENTORY,
    NATIVE_EQUIPPED,
    VIRTUAL_EQUIPPED,
    PENDING_REWARDS,
    OVERFLOW_CLAIM,
    QUARANTINE
}
