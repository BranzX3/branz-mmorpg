package com.branz.mmorpg.api.skill;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One allowlisted node in a declarative skill effect graph. */
public record SkillEffectNode(
        String id,
        SkillEffectType type,
        Map<String, Double> numbers,
        Map<String, String> values,
        List<String> children) {

    public SkillEffectNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("effect node id must not be blank");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(numbers, "numbers");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(children, "children");
        numbers.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("effect numbers must have finite values");
            }
        });
        numbers = Map.copyOf(numbers);
        values = Map.copyOf(values);
        children = List.copyOf(children);
    }
}
