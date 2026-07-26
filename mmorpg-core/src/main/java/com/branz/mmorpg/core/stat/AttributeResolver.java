package com.branz.mmorpg.core.stat;

import com.branz.mmorpg.api.stat.AttributeModifier;
import com.branz.mmorpg.api.stat.AttributeType;
import com.branz.mmorpg.api.stat.ModifierOperation;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The attribute pipeline, as a pure function.
 *
 * <pre>
 * base
 *   + flat modifiers
 *   -> additive percentage group (summed once, not compounded)
 *   -> multiplicative modifiers (one factor at a time)
 *   -> clamp to the attribute's documented range
 * </pre>
 *
 * <p>No state, no clock, no Paper: the same inputs always produce the same
 * number, which is what makes combat golden-testable.
 */
public final class AttributeResolver {

    private AttributeResolver() {
    }

    /**
     * Resolves one attribute.
     *
     * <p>Modifiers are sorted before application, so the result never depends on
     * the order they were added or on hash iteration order. Within a stacking
     * group only the strongest modifier contributes.
     */
    public static double resolve(AttributeType attribute, double base,
                                 Collection<AttributeModifier> modifiers) {
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(modifiers, "modifiers");
        if (!Double.isFinite(base)) {
            throw new IllegalArgumentException("base must be finite: " + base);
        }

        List<AttributeModifier> applicable = applicable(attribute, modifiers);

        double flat = base;
        double percentSum = 0.0;
        double product = 1.0;
        for (AttributeModifier modifier : applicable) {
            if (modifier.operation() == ModifierOperation.ADD_FLAT) {
                flat += modifier.value();
            }
        }
        for (AttributeModifier modifier : applicable) {
            if (modifier.operation() == ModifierOperation.ADD_PERCENT) {
                percentSum += modifier.value();
            }
        }
        for (AttributeModifier modifier : applicable) {
            if (modifier.operation() == ModifierOperation.MULTIPLY) {
                product *= modifier.value();
            }
        }

        double resolved = flat * (1.0 + percentSum) * product;
        if (!Double.isFinite(resolved)) {
            // Overflow to infinity is a broken stat sheet, not a very strong
            // player. Fall back to the documented cap rather than propagating it.
            return attribute.maximum();
        }
        return attribute.clamp(resolved);
    }

    /**
     * The modifiers that actually contribute: those for this attribute, reduced
     * to one winner per stacking group.
     */
    public static List<AttributeModifier> applicable(AttributeType attribute,
                                                     Collection<AttributeModifier> modifiers) {
        Map<String, AttributeModifier> groupWinners = new HashMap<>();
        List<AttributeModifier> ungrouped = new java.util.ArrayList<>();
        for (AttributeModifier modifier : modifiers) {
            if (modifier.attribute() != attribute) {
                continue;
            }
            if (!modifier.grouped()) {
                ungrouped.add(modifier);
                continue;
            }
            groupWinners.merge(modifier.stackingGroup(), modifier,
                    (existing, candidate) -> existing.compareTo(candidate) <= 0 ? existing : candidate);
        }
        List<AttributeModifier> applicable = new java.util.ArrayList<>(ungrouped);
        applicable.addAll(groupWinners.values());
        applicable.sort(null);
        return List.copyOf(applicable);
    }
}
