package com.branz.mmorpg.content.schema;

import java.util.List;

public record ReferenceRule(List<String> path, DefinitionType expectedType) {
    public ReferenceRule {
        path = List.copyOf(path);
    }

    public static ReferenceRule to(DefinitionType expectedType, String... path) {
        return new ReferenceRule(List.of(path), expectedType);
    }
}
