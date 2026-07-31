package com.branz.mmorpg.content.schema;

import java.util.List;

public record DefinitionSchema(
        DefinitionType type, List<FieldRule> fieldRules, List<ReferenceRule> referenceRules) {
    public DefinitionSchema {
        fieldRules = List.copyOf(fieldRules);
        referenceRules = List.copyOf(referenceRules);
    }
}
