package com.branz.mmorpg.core.character;

import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.CharacterClassProgress;
import com.branz.mmorpg.api.character.ClassSkillNodeDefinition;
import com.branz.mmorpg.api.character.ClassSkillNodeType;
import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure deterministic class XP, Skill Point, purchase, and respec rules. */
public final class CharacterClassProgressionEngine {
    private final CharacterClassDefinition definition;
    private final Map<ContentId, ClassSkillNodeDefinition> nodes;

    public CharacterClassProgressionEngine(CharacterClassDefinition definition,
                                           Map<ContentId, ClassSkillNodeDefinition> allNodes) {
        this.definition = Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(allNodes, "allNodes");
        this.nodes = allNodes.values().stream()
                .filter(node -> node.classId().equals(definition.id()))
                .collect(Collectors.toUnmodifiableMap(ClassSkillNodeDefinition::id, node -> node));
    }

    public long requiredXp(int level) {
        if (level < 1 || level >= definition.maximumLevel()) {
            throw new IllegalArgumentException("level outside class curve: " + level);
        }
        double raw = definition.xpCurveBase() * Math.pow(level, definition.xpCurveExponent());
        if (!Double.isFinite(raw) || raw > Long.MAX_VALUE) {
            throw new ArithmeticException("class XP curve overflow at level " + level);
        }
        return Math.max(1L, Math.round(raw));
    }

    public int levelFor(long totalXp) {
        if (totalXp < 0) throw new IllegalArgumentException("totalXp must not be negative");
        int level = 1;
        long remaining = totalXp;
        while (level < definition.maximumLevel()) {
            long needed = requiredXp(level);
            if (remaining < needed) break;
            remaining -= needed;
            level++;
        }
        return level;
    }

    public CharacterClassProgress grantXp(CharacterClassProgress current, long amount, Instant now) {
        requireOwner(current);
        if (amount < 0) throw new IllegalArgumentException("class XP must not be negative");
        long total = Math.addExact(current.totalXp(), amount);
        int nextLevel = levelFor(total);
        int granted = pointsEarnedAt(nextLevel) - pointsEarnedAt(current.level());
        return new CharacterClassProgress(current.playerId(), current.classId(), nextLevel, total,
                Math.addExact(current.unspentSkillPoints(), granted), current.treeRevision(),
                current.nodeRanks(), now);
    }

    public CharacterClassProgress purchase(CharacterClassProgress current, ContentId nodeId,
                                           Instant now) {
        requireOwner(current);
        ClassSkillNodeDefinition node = nodes.get(Objects.requireNonNull(nodeId, "nodeId"));
        if (node == null) throw new IllegalArgumentException("node does not belong to class " + nodeId);
        if (current.treeRevision() != definition.treeRevision()
                || node.treeRevision() != current.treeRevision()) {
            throw new IllegalStateException("class tree migration required");
        }
        int oldRank = current.rank(nodeId);
        if (oldRank >= node.maximumRank()) throw new IllegalStateException("node is at maximum rank");
        if (current.level() < node.requiredClassLevel()) {
            throw new IllegalStateException("class level " + node.requiredClassLevel() + " required");
        }
        node.prerequisites().forEach((required, rank) -> {
            if (current.rank(required) < rank) {
                throw new IllegalStateException("prerequisite " + required + " rank " + rank + " required");
            }
        });
        node.exclusionGroup().ifPresent(group -> nodes.values().stream()
                .filter(other -> !other.id().equals(node.id()))
                .filter(other -> other.exclusionGroup().filter(group::equals).isPresent())
                .filter(other -> current.rank(other.id()) > 0)
                .findFirst().ifPresent(conflict -> {
                    throw new IllegalStateException("mutually exclusive with " + conflict.id());
                }));
        if (current.unspentSkillPoints() < node.pointCostPerRank()) {
            throw new IllegalStateException("insufficient Class Skill Points");
        }
        Map<ContentId, Integer> ranks = new HashMap<>(current.nodeRanks());
        ranks.put(nodeId, oldRank + 1);
        return new CharacterClassProgress(current.playerId(), current.classId(), current.level(),
                current.totalXp(), current.unspentSkillPoints() - node.pointCostPerRank(),
                current.treeRevision(), ranks, now);
    }

    public CharacterClassProgress respec(CharacterClassProgress current, Instant now) {
        requireOwner(current);
        int refund = 0;
        for (Map.Entry<ContentId, Integer> rank : current.nodeRanks().entrySet()) {
            ClassSkillNodeDefinition node = nodes.get(rank.getKey());
            if (node == null) throw new IllegalStateException("class tree migration required for " + rank.getKey());
            refund = Math.addExact(refund, Math.multiplyExact(rank.getValue(), node.pointCostPerRank()));
        }
        return new CharacterClassProgress(current.playerId(), current.classId(), current.level(),
                current.totalXp(), Math.addExact(current.unspentSkillPoints(), refund),
                definition.treeRevision(), Map.of(), now);
    }

    public Set<ContentId> unlockedSkills(CharacterClassProgress progress) {
        requireOwner(progress);
        java.util.HashSet<ContentId> result = new java.util.HashSet<>(
                definition.starterGrantPlan().unlockedSkillIds());
        progress.nodeRanks().forEach((nodeId, rank) -> {
            if (rank > 0) {
                ClassSkillNodeDefinition node = nodes.get(nodeId);
                if (node != null) node.unlockedSkillId().ifPresent(result::add);
            }
        });
        return Set.copyOf(result);
    }

    public Map<ContentId, ClassSkillNodeDefinition> nodes() { return nodes; }

    private int pointsEarnedAt(int level) {
        int result = Math.max(0, level - 1);
        for (Integer bonus : definition.bonusSkillPointLevels()) if (bonus <= level) result++;
        return result;
    }

    private void requireOwner(CharacterClassProgress progress) {
        if (!progress.classId().equals(definition.id())) {
            throw new IllegalArgumentException("progress belongs to another class");
        }
    }
}
