package com.branz.mmorpg.api.stat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fully resolved attribute values at one instant.
 *
 * <p>Handed to combat, UI, and Quest instead of the live container, so a
 * calculation reads one coherent set of numbers even if a buff expires halfway
 * through it.
 */
public record AttributeSnapshot(Map<AttributeType, Double> values) {

    public AttributeSnapshot {
        Objects.requireNonNull(values, "values");
        EnumMap<AttributeType, Double> copy = new EnumMap<>(AttributeType.class);
        values.forEach((attribute, value) -> {
            Objects.requireNonNull(attribute, "attribute");
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("attribute snapshot value must be finite: " + attribute);
            }
            copy.put(attribute, value);
        });
        values = Map.copyOf(copy);
    }

    /** Snapshot in which every attribute sits at its default. */
    public static AttributeSnapshot defaults() {
        EnumMap<AttributeType, Double> values = new EnumMap<>(AttributeType.class);
        for (AttributeType attribute : AttributeType.values()) {
            values.put(attribute, attribute.defaultValue());
        }
        return new AttributeSnapshot(values);
    }

    public double get(AttributeType attribute) {
        Objects.requireNonNull(attribute, "attribute");
        return values.getOrDefault(attribute, attribute.defaultValue());
    }

    /** Attributes whose value differs from {@code other}. */
    public Map<AttributeType, Double> differenceFrom(AttributeSnapshot other) {
        Objects.requireNonNull(other, "other");
        EnumMap<AttributeType, Double> changed = new EnumMap<>(AttributeType.class);
        for (AttributeType attribute : AttributeType.values()) {
            double mine = get(attribute);
            if (Double.compare(mine, other.get(attribute)) != 0) {
                changed.put(attribute, mine);
            }
        }
        return changed;
    }
}
