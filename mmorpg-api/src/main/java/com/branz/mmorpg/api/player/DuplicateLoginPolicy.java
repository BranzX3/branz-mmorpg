package com.branz.mmorpg.api.player;

/**
 * What to do when a player logs in while a session for the same UUID still
 * exists — a common outcome of a proxy switch or an unclean disconnect, where
 * the old session has not been told the player is gone.
 */
public enum DuplicateLoginPolicy {

    /**
     * Mark the previous session {@link SessionState#CONFLICTED} and let the new
     * login proceed. The default: the old session is usually a ghost, and the
     * player holding the client should be able to play.
     *
     * <p>The conflicted session can no longer write, so it cannot overwrite the
     * new session's state with its stale snapshot.
     */
    CLOSE_PREVIOUS,

    /**
     * Refuse the new login while a session is live. Safer for valuable state,
     * at the cost of locking a player out until the ghost is reaped.
     */
    REJECT_NEW
}
