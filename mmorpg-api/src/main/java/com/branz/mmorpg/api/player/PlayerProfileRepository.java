package com.branz.mmorpg.api.player;

import com.branz.mmorpg.api.lifeskill.LifeSkillProfile;
import com.branz.mmorpg.api.lifeskill.LifeSkillMutationCommit;
import com.branz.mmorpg.api.lifeskill.LifeSkillSnapshot;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.operation.OperationId;
import java.util.function.UnaryOperator;
import java.util.UUID;

/**
 * Persistence port for player profiles and their Life Skill progression.
 *
 * <p>Defined here so core logic can be tested against an in-memory fake and the
 * JDBC implementation can live in the storage module without either depending on
 * the other.
 *
 * <p>Every method blocks and performs I/O. Callers must be off the tick thread.
 */
public interface PlayerProfileRepository {

    /**
     * Loads a profile, creating one if this player has never logged in.
     *
     * <p>Creation is insert-if-absent so two backends racing the same first
     * login cannot produce two profiles or lose one.
     *
     * @throws com.branz.mmorpg.api.error.MMOException with
     *         {@link com.branz.mmorpg.api.error.ErrorCode#STORAGE_FAILURE} on
     *         failure. Never returns a blank profile as a fallback.
     */
    PlayerProfile loadOrCreate(UUID playerId, String currentName);

    /** Loads Life Skill progression. An untouched player yields an empty profile. */
    LifeSkillProfile loadLifeSkills(UUID playerId);

    /**
     * Writes the profile. Called for periodic saves and at logout.
     *
     * @throws com.branz.mmorpg.api.error.MMOException with
     *         {@link com.branz.mmorpg.api.error.ErrorCode#STORAGE_FAILURE} on failure
     */
    void saveProfile(PlayerProfile profile);

    /**
     * Atomically writes the persistent parts of one runtime session.
     *
     * <p>Implementations backed by a transactional store must override this so
     * profile metadata and Life Skill progression cannot be committed
     * independently. The default keeps small test adapters source-compatible.
     */
    default void saveSession(PlayerProfile profile, LifeSkillProfile lifeSkills) {
        saveProfile(profile);
        saveLifeSkills(lifeSkills);
    }

    /**
     * Writes Life Skill progression and mastery-node ranks.
     *
     * <p>The default refuses the operation. Silently ignoring a dirty Life
     * Skill snapshot would report a successful save while losing progression.
     */
    default void saveLifeSkills(LifeSkillProfile lifeSkills) {
        throw new UnsupportedOperationException("Life Skill persistence is not implemented");
    }

    /**
     * Claims {@code operationId}, applies one pure mutation, and writes the
     * resulting Life Skill snapshot in the same transaction.
     */
    default LifeSkillMutationCommit mutateLifeSkill(
            UUID playerId,
            ContentId skillId,
            OperationId operationId,
            UnaryOperator<LifeSkillSnapshot> mutation) {
        throw new UnsupportedOperationException("atomic Life Skill mutations are not implemented");
    }
}
