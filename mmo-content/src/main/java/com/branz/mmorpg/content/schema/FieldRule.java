package com.branz.mmorpg.content.schema;

import java.util.List;
import java.util.Set;

public record FieldRule(
        List<String> path,
        FieldValueType type,
        boolean required,
        Double minimum,
        Double maximum,
        Integer minItems,
        Integer maxItems,
        Set<String> allowedValues,
        String unit,
        String description) {
    public FieldRule {
        path = List.copyOf(path);
        allowedValues = Set.copyOf(allowedValues);
        unit = unit == null ? "" : unit;
        description = description == null ? "" : description;
    }

    public String displayPath() {
        return String.join(".", path).replace(".*.", "[].");
    }
}
