package com.branz.mmorpg.content.definition;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.content.schema.DefinitionType;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DefinitionRegistry {
    private final Map<DefinitionId, ContentDefinition> definitions;

    private DefinitionRegistry(Map<DefinitionId, ContentDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static DefinitionRegistry of(Collection<ContentDefinition> definitions) {
        LinkedHashMap<DefinitionId, ContentDefinition> indexed = new LinkedHashMap<>();
        for (ContentDefinition definition : definitions) {
            ContentDefinition previous = indexed.putIfAbsent(definition.id(), definition);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate definition ID: " + definition.id());
            }
        }
        return new DefinitionRegistry(indexed);
    }

    public Optional<ContentDefinition> find(DefinitionId id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Collection<ContentDefinition> all() {
        return definitions.values();
    }

    public List<ContentDefinition> byType(DefinitionType type) {
        return definitions.values().stream()
                .filter(definition -> definition.type() == type)
                .toList();
    }

    public int size() {
        return definitions.size();
    }
}
