package com.branz.mmorpg.api.player;

/**
 * Lifecycle of a runtime player session.
 *
 * <pre>
 * ABSENT -> LOADING -> ACTIVE -> SAVING -> CLOSED
 *              |          |        |
 *              v          v        v
 *        LOAD_FAILED  CONFLICTED  SAVE_RETRY_PENDING -> CLOSED
 * </pre>
 *
 * <p>Only {@link #ACTIVE} permits gameplay mutation. Every other state — including
 * the failure states — must refuse it rather than fall back to a blank profile.
 */
public enum SessionState {

    /** No session exists for this player. */
    ABSENT,

    /** Profile load is in flight. Gameplay mutation is refused. */
    LOADING,

    /** Loaded and playable. The only state in which progress may change. */
    ACTIVE,

    /** A save is in flight; mutation is refused so the snapshot cannot move under it. */
    SAVING,

    /** Lifecycle finished. The session object is inert and its token is dead. */
    CLOSED,

    /** Load failed. Gameplay stays disabled; the player is not given an empty profile. */
    LOAD_FAILED,

    /** Logout save failed and is queued for bounded retry with a durable record. */
    SAVE_RETRY_PENDING,

    /** A newer session took ownership of this player. This one may no longer write. */
    CONFLICTED;

    /** Whether gameplay mutation is permitted. */
    public boolean playable() {
        return this == ACTIVE;
    }

    /** Whether the lifecycle is over and the session may be discarded. */
    public boolean terminal() {
        return this == CLOSED || this == CONFLICTED || this == LOAD_FAILED;
    }

    /** Whether this state means the profile was never successfully loaded. */
    public boolean loadFailure() {
        return this == LOAD_FAILED;
    }

    public boolean canTransitionTo(SessionState target) {
        if (target == null) {
            return false;
        }
        // A newer login may conflict any live session at any point.
        if (target == CONFLICTED) {
            return this == LOADING || this == ACTIVE || this == SAVING;
        }
        return switch (this) {
            case ABSENT -> target == LOADING;
            case LOADING -> target == ACTIVE || target == LOAD_FAILED;
            case ACTIVE -> target == SAVING;
            case SAVING -> target == ACTIVE || target == CLOSED || target == SAVE_RETRY_PENDING;
            case SAVE_RETRY_PENDING -> target == SAVING || target == CLOSED;
            case LOAD_FAILED -> target == CLOSED;
            case CLOSED, CONFLICTED -> false;
        };
    }
}
