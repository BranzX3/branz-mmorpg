package com.branz.mmorpg.content;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class ImmutableContentSnapshot implements ContentSnapshot {
    private final long revision;
    private final Instant loadedAt;
    private final Map<ContentId, ContentDefinition> definitions;
    private final Map<ContentId, MaterialDefinition> materials;

    ImmutableContentSnapshot(long revision, Instant loadedAt, Map<ContentId, ContentDefinition> definitions) {
        this.revision = revision;
        this.loadedAt = loadedAt;
        this.definitions = Map.copyOf(definitions);
        Map<ContentId, MaterialDefinition> materialIndex = new LinkedHashMap<>();
        definitions.forEach((id, definition) -> {
            if (definition instanceof MaterialDefinition material) {
                materialIndex.put(id, material);
            }
        });
        this.materials = Map.copyOf(materialIndex);
    }

    static ImmutableContentSnapshot empty() {
        return new ImmutableContentSnapshot(0, Instant.EPOCH, Map.of());
    }

    @Override
    public long revision() {
        return revision;
    }

    @Override
    public Instant loadedAt() {
        return loadedAt;
    }

    @Override
    public Collection<ContentDefinition> definitions() {
        return definitions.values();
    }

    @Override
    public Optional<ContentDefinition> find(ContentId id) {
        return Optional.ofNullable(definitions.get(id));
    }

    @Override
    public <T extends ContentDefinition> Optional<T> find(ContentId id, Class<T> type) {
        return find(id).filter(type::isInstance).map(type::cast);
    }

    @Override
    public Map<ContentId, MaterialDefinition> materials() {
        return materials;
    }
}
