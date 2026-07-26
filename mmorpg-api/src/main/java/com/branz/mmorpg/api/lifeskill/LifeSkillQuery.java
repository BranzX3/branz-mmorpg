package com.branz.mmorpg.api.lifeskill;

import com.branz.mmorpg.api.content.ContentId;
import java.util.UUID;

/**
 * Read-only view of Life Skill progression, and the only surface Quest, the UI,
 * and gathering content may depend on.
 *
 * <p>Deliberately query-only. Every mutation — XP grants, level-ups, point
 * spending, respecs — arrives with the progression engine (S1) behind an
 * idempotent operation ID, so no caller can be tempted to write progress through
 * a convenience setter that skips the audit and the transaction.
 *
 * <p>Implementations serve the session's in-memory snapshot and do not touch
 * storage, so these calls are safe on a tick thread.
 */
public interface LifeSkillQuery {

    /**
     * Life Skill profile of a loaded player.
     *
     * @throws com.branz.mmorpg.api.error.MMOException with
     *         {@link com.branz.mmorpg.api.error.ErrorCode#PROFILE_LOAD_FAILED}
     *         when the player has no successfully loaded session. Callers must
     *         fail closed rather than substitute an empty profile: an untrained
     *         player and an unloaded one are not the same thing.
     */
    LifeSkillProfile profile(UUID playerId);

    /** Snapshot of one skill, untrained when the player has never trained it. */
    default LifeSkillSnapshot skill(UUID playerId, ContentId skillId) {
        return profile(playerId).skill(skillId);
    }

    default int level(UUID playerId, ContentId skillId) {
        return skill(playerId, skillId).level();
    }

    /** Whether the player owns {@code nodeId} at {@code minimumRank} or better. */
    default boolean hasNode(UUID playerId, ContentId skillId, ContentId nodeId, int minimumRank) {
        return skill(playerId, skillId).hasNode(nodeId, minimumRank);
    }
}
