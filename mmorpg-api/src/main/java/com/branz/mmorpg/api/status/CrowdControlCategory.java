package com.branz.mmorpg.api.status;

/**
 * Crowd-control class of a status.
 *
 * <p>The class, not the individual status, is what immunity and diminishing
 * rules key on: being immune to stun means immune to every stun, not to one
 * particular spell.
 */
public enum CrowdControlCategory {

    /** Not crowd control. */
    NONE,

    /** Reduces movement speed. */
    SLOW,

    /** Prevents movement, allows acting. */
    ROOT,

    /** Prevents movement and acting. */
    STUN,

    /** Prevents skill use, allows movement. */
    SILENCE;

    /** Whether crowd-control resistance shortens this. */
    public boolean resistible() {
        return this != NONE;
    }
}
