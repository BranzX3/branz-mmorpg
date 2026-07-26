package com.branz.mmorpg.api.lifeskill;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One Life Skill as seen by a caller: its counters plus the mastery ranks the
 * player owns.
 *
 * <p>Deeply immutable. {@code nodeRanks} is defensively copied on construction
 * and the accessor hands back an unmodifiable view, so handing a snapshot to
 * Quest, to the UI, or across a scheduler boundary cannot leak a mutable
 * collection into another subsystem.
 *
 * @param progress  counters for this skill
 * @param nodeRanks purchased mastery node ranks; a node absent from the map is
 *                  simply unowned, and no entry may have a rank below 1
 */
public record LifeSkillSnapshot(LifeSkillProgress progress, Map<ContentId, Integer> nodeRanks) {

    public LifeSkillSnapshot {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(nodeRanks, "nodeRanks");
        nodeRanks.forEach((nodeId, rank) -> {
            Objects.requireNonNull(nodeId, "nodeId");
            if (rank == null || rank < 1) {
                throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                        "node rank must be at least 1: " + nodeId + " = " + rank);
            }
        });
        nodeRanks = Map.copyOf(nodeRanks);
    }

    public static LifeSkillSnapshot untrained(ContentId skillId, Instant at) {
        return new LifeSkillSnapshot(LifeSkillProgress.untrained(skillId, at), Map.of());
    }

    public ContentId skillId() {
        return progress.skillId();
    }

    public int level() {
        return progress.level();
    }

    public long totalXp() {
        return progress.totalXp();
    }

    public int unspentPoints() {
        return progress.unspentPoints();
    }

    /** Owned rank of {@code nodeId}, or 0 when the node is not owned. */
    public int rankOf(ContentId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId");
        return nodeRanks.getOrDefault(nodeId, 0);
    }

    public boolean hasNode(ContentId nodeId) {
        return rankOf(nodeId) > 0;
    }

    public boolean hasNode(ContentId nodeId, int minimumRank) {
        return rankOf(nodeId) >= minimumRank;
    }
}
