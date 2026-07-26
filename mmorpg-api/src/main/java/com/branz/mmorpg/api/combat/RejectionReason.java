package com.branz.mmorpg.api.combat;

/**
 * Why a damage attempt did nothing.
 *
 * <p>Every rejection is named. "It just didn't work" is unusable for a player,
 * unfalsifiable for a bug report, and invisible to abuse telemetry.
 */
public enum RejectionReason {

    /** Attacker or target is not in a state that permits combat. */
    ATTACKER_UNAVAILABLE,
    TARGET_UNAVAILABLE,

    /** Target is already dead. */
    TARGET_DEAD,

    /** Target is temporarily immune to all damage. */
    TARGET_INVULNERABLE,

    /** Attacker or target stands in a safe zone. */
    SAFE_ZONE,

    /** Different worlds; no interaction is possible. */
    DIFFERENT_WORLD,

    /** Beyond the effect's permitted range. */
    OUT_OF_RANGE,

    /** No line of sight to the target. */
    NO_LINE_OF_SIGHT,

    /** Same party or faction, and friendly fire is off. */
    FRIENDLY_FIRE_DISABLED,

    /** Player-versus-player is disabled on this server or in this region. */
    PVP_DISABLED,

    /** This cast already hit this target as many times as its definition allows. */
    DUPLICATE_HIT,

    /** The attempt carried a non-positive or non-finite power. */
    INVALID_POWER;

    public boolean rejected() {
        return true;
    }
}
