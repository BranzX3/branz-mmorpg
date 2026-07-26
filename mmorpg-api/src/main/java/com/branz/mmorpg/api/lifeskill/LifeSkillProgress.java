package com.branz.mmorpg.api.lifeskill;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable counters for one Life Skill.
 *
 * <p>Pure stored state: no formula lives here. Deriving a level from
 * {@code totalXp}, awarding points at milestones, and validating a level against
 * the curve all belong to the progression engine (S1), because those rules are
 * content-driven and change with balance while this shape does not.
 *
 * @param skillId       skill this progress belongs to, e.g. {@code branz:mining}
 * @param level         current level, at least 1
 * @param totalXp       lifetime XP, never negative and never decreasing
 * @param unspentPoints mastery points not yet spent
 * @param treeRevision  content revision the mastery tree was last reconciled against
 * @param updatedAt     when this row was last written
 */
public record LifeSkillProgress(
        ContentId skillId,
        int level,
        long totalXp,
        int unspentPoints,
        long treeRevision,
        Instant updatedAt) {

    /** Level of a player who has never trained the skill. */
    public static final int STARTING_LEVEL = 1;

    public LifeSkillProgress {
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (level < STARTING_LEVEL) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "level must be at least " + STARTING_LEVEL + ": " + level);
        }
        if (totalXp < 0) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT, "totalXp must not be negative: " + totalXp);
        }
        if (unspentPoints < 0) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "unspentPoints must not be negative: " + unspentPoints);
        }
        if (treeRevision < 0) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "treeRevision must not be negative: " + treeRevision);
        }
    }

    /**
     * Progress of a player who has never trained {@code skillId}.
     *
     * <p>Untrained is a real value, not null and not absent. Every query path
     * returns one of these rather than an empty optional, so no caller can
     * accidentally treat "not started" as "failed to load" — the fail-closed
     * rule applies to load failures only, and those throw.
     */
    public static LifeSkillProgress untrained(ContentId skillId, Instant at) {
        return new LifeSkillProgress(skillId, STARTING_LEVEL, 0L, 0, 0L, at);
    }

    /** Whether the player has any recorded progress in this skill. */
    public boolean started() {
        return totalXp > 0 || level > STARTING_LEVEL || unspentPoints > 0;
    }
}
