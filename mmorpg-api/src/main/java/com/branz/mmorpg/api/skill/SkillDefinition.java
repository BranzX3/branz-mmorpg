package com.branz.mmorpg.api.skill;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, platform-independent skill definition.
 *
 * <p>The effect graph contains only allowlisted node types and is validated as
 * a DAG. Content cannot name Java classes or execute commands.
 */
public record SkillDefinition(
        ContentId id,
        String displayName,
        String inputSlot,
        Set<String> tags,
        long castMillis,
        long activeMillis,
        long recoveryMillis,
        long cooldownMillis,
        String cooldownGroup,
        Map<ResourceType, Double> costs,
        double interruptRefundFraction,
        double range,
        boolean requiresLineOfSight,
        Map<String, SkillEffectNode> effects,
        String rootEffect) implements ContentDefinition {

    public SkillDefinition {
        Objects.requireNonNull(id, "id");
        displayName = displayName == null || displayName.isBlank() ? id.value() : displayName.trim();
        if (inputSlot == null || inputSlot.isBlank()) {
            throw invalid(id, "inputSlot must not be blank");
        }
        Objects.requireNonNull(tags, "tags");
        Objects.requireNonNull(costs, "costs");
        Objects.requireNonNull(effects, "effects");
        if (castMillis < 0 || activeMillis < 0 || recoveryMillis < 0 || cooldownMillis < 0) {
            throw invalid(id, "skill timings must not be negative");
        }
        if (cooldownMillis == 0) {
            throw invalid(id, "cooldownMillis must be positive");
        }
        cooldownGroup = cooldownGroup == null || cooldownGroup.isBlank()
                ? id.toString() : cooldownGroup.trim();
        if (!Double.isFinite(interruptRefundFraction)
                || interruptRefundFraction < 0.0 || interruptRefundFraction > 1.0) {
            throw invalid(id, "interruptRefundFraction must be in [0,1]");
        }
        if (!Double.isFinite(range) || range < 0.0) {
            throw invalid(id, "range must be finite and non-negative");
        }
        costs.forEach((resource, amount) -> {
            if (resource == null || amount == null || !Double.isFinite(amount) || amount < 0.0) {
                throw invalid(id, "resource costs must be finite and non-negative");
            }
        });
        if (rootEffect == null || !effects.containsKey(rootEffect)) {
            throw invalid(id, "rootEffect must reference an effect node");
        }
        for (Map.Entry<String, SkillEffectNode> entry : effects.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) {
                throw invalid(id, "effect map key must match node id " + entry.getKey());
            }
            for (String child : entry.getValue().children()) {
                if (!effects.containsKey(child)) {
                    throw invalid(id, "effect " + entry.getKey() + " references unknown child " + child);
                }
            }
        }
        validateAcyclic(id, effects, rootEffect, new HashSet<>(), new HashSet<>());
        tags = Set.copyOf(tags);
        costs = Map.copyOf(costs);
        effects = Map.copyOf(effects);
    }

    @Override
    public ContentType type() {
        return ContentType.SKILL;
    }

    private static void validateAcyclic(ContentId id, Map<String, SkillEffectNode> effects,
                                        String node, Set<String> visiting, Set<String> visited) {
        if (visited.contains(node)) {
            return;
        }
        if (!visiting.add(node)) {
            throw invalid(id, "effect graph contains a cycle at " + node);
        }
        for (String child : effects.get(node).children()) {
            validateAcyclic(id, effects, child, visiting, visited);
        }
        visiting.remove(node);
        visited.add(node);
    }

    private static MMOException invalid(ContentId id, String detail) {
        return new MMOException(ErrorCode.CONTENT_INVALID, id + ": " + detail);
    }
}
