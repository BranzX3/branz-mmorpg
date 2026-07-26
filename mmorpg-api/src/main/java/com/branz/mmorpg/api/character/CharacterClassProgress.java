package com.branz.mmorpg.api.character;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable persistent class-level, Skill Point, and node-rank state. */
public record CharacterClassProgress(
        UUID playerId,
        ContentId classId,
        int level,
        long totalXp,
        int unspentSkillPoints,
        int treeRevision,
        Map<ContentId, Integer> nodeRanks,
        Instant updatedAt) {

    public CharacterClassProgress {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(classId, "classId");
        Objects.requireNonNull(nodeRanks, "nodeRanks");
        Objects.requireNonNull(updatedAt, "updatedAt");
        nodeRanks = Map.copyOf(nodeRanks);
        if (level < 1 || totalXp < 0 || unspentSkillPoints < 0 || treeRevision < 1) {
            throw new IllegalArgumentException("invalid character class progress");
        }
        nodeRanks.forEach((node, rank) -> {
            if (rank == null || rank < 1) throw new IllegalArgumentException("invalid node rank " + node);
        });
    }

    public static CharacterClassProgress initial(UUID playerId, ContentId classId,
                                                 int treeRevision, Instant now) {
        return new CharacterClassProgress(playerId, classId, 1, 0L, 0,
                treeRevision, Map.of(), now);
    }

    public int rank(ContentId nodeId) { return nodeRanks.getOrDefault(nodeId, 0); }
}
