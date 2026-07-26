package com.branz.mmorpg.core.lifeskill;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillProgress;
import com.branz.mmorpg.api.lifeskill.LifeSkillSnapshot;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure formulas and mastery-tree transactions for one Life Skill. */
public final class LifeSkillProgressionEngine {

    private final LifeSkillDefinition skill;
    private final Map<ContentId, LifeSkillNodeDefinition> nodes;

    public LifeSkillProgressionEngine(LifeSkillDefinition skill,
                                      Map<ContentId, LifeSkillNodeDefinition> nodes) {
        this.skill = Objects.requireNonNull(skill, "skill");
        this.nodes = Map.copyOf(nodes);
        validateTree();
    }

    /** Cumulative XP threshold at curve index {@code level}. */
    public long requiredXp(int level) {
        if (level < 1 || level >= skill.maximumLevel()) {
            throw new IllegalArgumentException("level outside skill curve: " + level);
        }
        double required = skill.curveBase() * Math.pow(level, skill.curveExponent());
        if (!Double.isFinite(required) || required > Long.MAX_VALUE) {
            throw new ArithmeticException("Life Skill XP curve overflow at " + level);
        }
        return Math.max(1L, Math.round(required));
    }

    /** Total XP at the start of {@code level}. */
    public long threshold(int level) {
        if (level < 1 || level > skill.maximumLevel()) {
            throw new IllegalArgumentException("level outside skill curve: " + level);
        }
        if (level == 1) return 0L;
        return requiredXp(level - 1);
    }

    public int levelFor(long totalXp) {
        if (totalXp < 0) {
            throw new IllegalArgumentException("totalXp must not be negative");
        }
        int level = 1;
        while (level < skill.maximumLevel() && totalXp >= threshold(level + 1)) level++;
        return level;
    }

    public Mutation award(LifeSkillSnapshot current, long xp, long treeRevision, Instant now) {
        requireSkill(current);
        if (xp < 0) {
            throw new IllegalArgumentException("XP must not be negative");
        }
        long cap = threshold(skill.maximumLevel());
        long awardable = Math.max(0L, cap - current.totalXp());
        long awarded = Math.min(xp, awardable);
        long total = Math.addExact(current.totalXp(), awarded);
        int level = levelFor(total);
        int points = current.unspentPoints();
        for (int crossed = current.level() + 1; crossed <= level; crossed++) {
            if (skill.pointMilestones().contains(crossed)) {
                points++;
            }
        }
        LifeSkillProgress progress = new LifeSkillProgress(skill.id(), level, total, points,
                treeRevision, now);
        return new Mutation(current, new LifeSkillSnapshot(progress, current.nodeRanks()),
                awarded, level - current.level(), points - current.unspentPoints());
    }

    public Mutation purchase(LifeSkillSnapshot current, ContentId nodeId, Instant now) {
        requireSkill(current);
        LifeSkillNodeDefinition node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("unknown mastery node " + nodeId);
        }
        int rank = current.rankOf(nodeId);
        if (rank >= node.maximumRank()) {
            throw new IllegalStateException(nodeId + " is already at maximum rank");
        }
        if (current.level() < node.requiredLevel()) {
            throw new IllegalStateException("requires level " + node.requiredLevel());
        }
        node.prerequisites().forEach((required, requiredRank) -> {
            if (!current.hasNode(required, requiredRank)) {
                throw new IllegalStateException("missing prerequisite " + required
                        + " rank " + requiredRank);
            }
        });
        if (current.unspentPoints() < node.pointCostPerRank()) {
            throw new IllegalStateException("not enough Life Skill points");
        }
        Map<ContentId, Integer> ranks = new HashMap<>(current.nodeRanks());
        ranks.put(nodeId, rank + 1);
        LifeSkillProgress old = current.progress();
        LifeSkillProgress progress = new LifeSkillProgress(skill.id(), old.level(), old.totalXp(),
                old.unspentPoints() - node.pointCostPerRank(), old.treeRevision(), now);
        return new Mutation(current, new LifeSkillSnapshot(progress, ranks), 0L, 0,
                -node.pointCostPerRank());
    }

    public Mutation respec(LifeSkillSnapshot current, Instant now) {
        requireSkill(current);
        int refund = 0;
        for (Map.Entry<ContentId, Integer> rank : current.nodeRanks().entrySet()) {
            LifeSkillNodeDefinition node = nodes.get(rank.getKey());
            if (node == null) {
                throw new IllegalStateException("cannot respec unknown node " + rank.getKey());
            }
            refund = Math.addExact(refund, Math.multiplyExact(rank.getValue(),
                    node.pointCostPerRank()));
        }
        LifeSkillProgress old = current.progress();
        LifeSkillProgress progress = new LifeSkillProgress(skill.id(), old.level(), old.totalXp(),
                Math.addExact(old.unspentPoints(), refund), old.treeRevision(), now);
        return new Mutation(current, new LifeSkillSnapshot(progress, Map.of()), 0L, 0, refund);
    }

    private void validateTree() {
        for (LifeSkillNodeDefinition node : nodes.values()) {
            if (!node.skillId().equals(skill.id())) {
                throw new IllegalArgumentException(node.id() + " belongs to another skill");
            }
            if (node.requiredLevel() > skill.maximumLevel()) {
                throw new IllegalArgumentException(node.id() + " requires an impossible level");
            }
            node.prerequisites().forEach((required, rank) -> {
                LifeSkillNodeDefinition prerequisite = nodes.get(required);
                if (prerequisite == null) {
                    throw new IllegalArgumentException(node.id() + " has unknown prerequisite " + required);
                }
                if (rank > prerequisite.maximumRank()) {
                    throw new IllegalArgumentException(node.id() + " requires impossible rank of " + required);
                }
            });
        }
        Set<ContentId> visited = new HashSet<>();
        Set<ContentId> visiting = new HashSet<>();
        for (ContentId node : nodes.keySet()) {
            visit(node, visiting, visited);
        }
    }

    private void visit(ContentId nodeId, Set<ContentId> visiting, Set<ContentId> visited) {
        if (visited.contains(nodeId)) {
            return;
        }
        if (!visiting.add(nodeId)) {
            throw new IllegalArgumentException("mastery tree cycle at " + nodeId);
        }
        for (ContentId prerequisite : nodes.get(nodeId).prerequisites().keySet()) {
            visit(prerequisite, visiting, visited);
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
    }

    private void requireSkill(LifeSkillSnapshot snapshot) {
        if (!snapshot.skillId().equals(skill.id())) {
            throw new IllegalArgumentException("snapshot belongs to " + snapshot.skillId());
        }
    }

    public record Mutation(LifeSkillSnapshot before, LifeSkillSnapshot after,
                           long awardedXp, int levelsGained, int pointDelta) {
    }
}
