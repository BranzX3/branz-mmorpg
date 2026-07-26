package com.branz.mmorpg.core.mastery;

import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.mastery.MasterySnapshot;
import java.time.Instant;
import java.util.Objects;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.mastery.MasteryNodeDefinition;
import java.util.HashMap;
import java.util.Map;

/** Pure combat-mastery curve, bounded bonus, and anti-farm award calculation. */
public final class CombatMasteryEngine {

    private final MasteryDefinition definition;
    private final Map<ContentId, MasteryNodeDefinition> nodes;

    public CombatMasteryEngine(MasteryDefinition definition) {
        this(definition, Map.of());
    }

    public CombatMasteryEngine(MasteryDefinition definition,
                               Map<ContentId, MasteryNodeDefinition> allNodes) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.nodes = allNodes.values().stream()
                .filter(node -> node.masteryId().equals(definition.id()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        MasteryNodeDefinition::id, node -> node));
    }

    public long requiredXp(int level) {
        if (level < 1 || level > definition.maximumLevel()) {
            throw new IllegalArgumentException("level outside mastery curve");
        }
        return Math.max(1L, Math.round(
                definition.curveBase() * Math.pow(level, definition.curveExponent())));
    }

    public int levelFor(long totalXp) {
        if (totalXp < 0) {
            throw new IllegalArgumentException("totalXp must not be negative");
        }
        int level = 1;
        long remaining = totalXp;
        while (level < definition.maximumLevel() && remaining >= requiredXp(level)) {
            remaining -= requiredXp(level);
            level++;
        }
        return level;
    }

    public long awardAmount(long baseXp, double antiFarmMultiplier) {
        if (baseXp < 0 || !Double.isFinite(antiFarmMultiplier)
                || antiFarmMultiplier < 0.0 || antiFarmMultiplier > 1.0) {
            throw new IllegalArgumentException("invalid mastery award");
        }
        return (long) Math.floor(baseXp * antiFarmMultiplier);
    }

    public MasterySnapshot award(MasterySnapshot current, long awardedXp, Instant now) {
        if (!current.masteryId().equals(definition.id())) {
            throw new IllegalArgumentException("snapshot belongs to another mastery");
        }
        long total = Math.addExact(current.totalXp(), awardedXp);
        int level = levelFor(total);
        int points = Math.addExact(current.unspentPoints(), level - current.level());
        return new MasterySnapshot(definition.id(), level, total, points,
                current.treeRevision(), current.nodeRanks(), now);
    }

    public MasterySnapshot purchase(MasterySnapshot current, ContentId nodeId, Instant now) {
        requireOwner(current);
        MasteryNodeDefinition node = nodes.get(Objects.requireNonNull(nodeId, "nodeId"));
        if (node == null) throw new IllegalArgumentException("node does not belong to mastery " + nodeId);
        if (current.treeRevision() != definition.treeRevision()
                || node.treeRevision() != current.treeRevision()) {
            throw new IllegalStateException("mastery tree migration required");
        }
        int oldRank = current.rank(nodeId);
        if (oldRank >= node.maximumRank()) throw new IllegalStateException("node is at maximum rank");
        if (current.level() < node.requiredMasteryLevel()) {
            throw new IllegalStateException("mastery level " + node.requiredMasteryLevel() + " required");
        }
        node.prerequisites().forEach((required, rank) -> {
            if (current.rank(required) < rank) {
                throw new IllegalStateException("prerequisite " + required + " rank " + rank + " required");
            }
        });
        node.exclusionGroup().ifPresent(group -> nodes.values().stream()
                .filter(other -> !other.id().equals(node.id()))
                .filter(other -> other.exclusionGroup().filter(group::equals).isPresent())
                .filter(other -> current.rank(other.id()) > 0).findFirst().ifPresent(conflict -> {
                    throw new IllegalStateException("mutually exclusive with " + conflict.id());
                }));
        if (current.unspentPoints() < node.pointCostPerRank()) {
            throw new IllegalStateException("insufficient Combat Mastery Points");
        }
        Map<ContentId, Integer> ranks = new HashMap<>(current.nodeRanks());
        ranks.put(nodeId, oldRank + 1);
        return new MasterySnapshot(current.masteryId(), current.level(), current.totalXp(),
                current.unspentPoints() - node.pointCostPerRank(), current.treeRevision(), ranks, now);
    }

    public MasterySnapshot respec(MasterySnapshot current, Instant now) {
        requireOwner(current);
        int refund = 0;
        for (Map.Entry<ContentId, Integer> rank : current.nodeRanks().entrySet()) {
            MasteryNodeDefinition node = nodes.get(rank.getKey());
            if (node == null) throw new IllegalStateException("mastery tree migration required");
            refund = Math.addExact(refund,
                    Math.multiplyExact(rank.getValue(), node.pointCostPerRank()));
        }
        return new MasterySnapshot(current.masteryId(), current.level(), current.totalXp(),
                Math.addExact(current.unspentPoints(), refund), definition.treeRevision(),
                Map.of(), now);
    }

    public double powerBonus(MasterySnapshot snapshot) {
        double progress = definition.maximumLevel() <= 1 ? 1.0
                : (snapshot.level() - 1.0) / (definition.maximumLevel() - 1.0);
        return Math.min(definition.maximumPowerBonus(),
                Math.max(0.0, progress * definition.maximumPowerBonus()));
    }

    public Map<ContentId, MasteryNodeDefinition> nodes() { return nodes; }

    private void requireOwner(MasterySnapshot snapshot) {
        if (!snapshot.masteryId().equals(definition.id())) {
            throw new IllegalArgumentException("snapshot belongs to another mastery");
        }
    }
}
