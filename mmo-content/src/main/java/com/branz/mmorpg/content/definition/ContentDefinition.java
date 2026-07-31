package com.branz.mmorpg.content.definition;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.content.schema.DefinitionType;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable compiled definition model shared by runtime and authoring tools. */
public final class ContentDefinition {
    private final DefinitionId id;
    private final DefinitionType type;
    private final int schemaVersion;
    private final Path source;
    private final JsonNode body;
    private final List<String> sourceLines;

    public ContentDefinition(
            DefinitionId id,
            DefinitionType type,
            int schemaVersion,
            Path source,
            JsonNode body,
            List<String> sourceLines) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        this.schemaVersion = schemaVersion;
        this.source = Objects.requireNonNull(source, "source");
        this.body = Objects.requireNonNull(body, "body").deepCopy();
        this.sourceLines = List.copyOf(sourceLines);
    }

    public DefinitionId id() {
        return id;
    }

    public DefinitionType type() {
        return type;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public Path source() {
        return source;
    }

    public JsonNode body() {
        return body.deepCopy();
    }

    public List<String> sourceLines() {
        return sourceLines;
    }
}
