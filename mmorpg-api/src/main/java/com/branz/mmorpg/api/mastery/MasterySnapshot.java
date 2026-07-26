package com.branz.mmorpg.api.mastery;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Objects;
import java.util.Map;

public record MasterySnapshot(
        ContentId masteryId,
        int level,
        long totalXp,
        int unspentPoints,
        int treeRevision,
        Map<ContentId, Integer> nodeRanks,
        Instant updatedAt) {

    public MasterySnapshot {
        Objects.requireNonNull(masteryId, "masteryId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        nodeRanks = Map.copyOf(Objects.requireNonNull(nodeRanks, "nodeRanks"));
        if (level < 1 || totalXp < 0 || unspentPoints < 0 || treeRevision < 1) {
            throw new IllegalArgumentException("mastery level/XP is invalid");
        }
        nodeRanks.forEach((node, rank) -> {
            if (rank == null || rank < 1) throw new IllegalArgumentException("invalid mastery node rank");
        });
    }

    public MasterySnapshot(ContentId masteryId, int level, long totalXp, Instant updatedAt) {
        this(masteryId, level, totalXp, Math.max(0, level - 1), 1, Map.of(), updatedAt);
    }

    public static MasterySnapshot untrained(ContentId id, Instant at) {
        return new MasterySnapshot(id, 1, 0L, 0, 1, Map.of(), at);
    }

    public static MasterySnapshot untrained(ContentId id, int treeRevision, Instant at) {
        return new MasterySnapshot(id, 1, 0L, 0, treeRevision, Map.of(), at);
    }

    public int rank(ContentId nodeId) { return nodeRanks.getOrDefault(nodeId, 0); }
}
