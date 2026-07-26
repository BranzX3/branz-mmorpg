package com.branz.mmorpg.api.player;

import com.branz.mmorpg.api.lifeskill.LifeSkillProfile;
import java.util.UUID;

/**
 * Read-only view of one player's runtime session.
 *
 * <p>Carries no Bukkit type. A session outlives individual async hops and is the
 * thing gameplay code checks before mutating anything:
 *
 * <pre>{@code
 * SessionToken captured = session.token();
 * scheduler.async(() -> load())
 *          .thenAccept(result -> scheduler.sync(() -> {
 *              if (!sessions.isLive(captured)) {
 *                  return; // player logged out or relogged; drop the result
 *              }
 *              ...
 *          }));
 * }</pre>
 */
public interface PlayerSession {

    UUID playerId();

    /** Token of this login. Compare it before applying any late async result. */
    SessionToken token();

    SessionState state();

    /**
     * Immutable profile snapshot.
     *
     * @throws com.branz.mmorpg.api.error.MMOException with
     *         {@link com.branz.mmorpg.api.error.ErrorCode#PROFILE_LOAD_FAILED}
     *         when the profile never loaded. There is deliberately no blank
     *         fallback: an empty profile would silently erase a returning
     *         player's progress on the next save.
     */
    PlayerProfile profile();

    /** Life Skill progression as loaded for this session. */
    LifeSkillProfile lifeSkills();

    /** Content revision this session began with. */
    long contentRevision();

    /** Whether gameplay mutation is currently permitted. */
    default boolean playable() {
        return state().playable();
    }
}
