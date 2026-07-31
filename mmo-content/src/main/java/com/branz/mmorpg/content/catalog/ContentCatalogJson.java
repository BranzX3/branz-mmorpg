package com.branz.mmorpg.content.catalog;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;

/** Canonical JSON projection shared by catalog artifacts and the local HTTP service. */
public final class ContentCatalogJson {
    private final ObjectMapper mapper = new ObjectMapper();

    public ObjectNode catalog(ContentCatalog catalog) {
        ObjectNode root = mapper.createObjectNode();
        root.put("contentVersion", catalog.contentVersion());
        ArrayNode definitions = root.putArray("definitions");
        catalog.entries().forEach(entry -> definitions.add(definition(entry)));
        return root;
    }

    public ObjectNode search(ContentCatalog catalog, String query) {
        ObjectNode root = mapper.createObjectNode();
        root.put("contentVersion", catalog.contentVersion());
        root.put("query", query);
        ArrayNode matches = root.putArray("matches");
        catalog.search(query).forEach(entry -> matches.add(definition(entry)));
        root.put("count", matches.size());
        return root;
    }

    public ObjectNode definition(ContentCatalogEntry entry) {
        ObjectNode item = mapper.createObjectNode();
        item.put("id", entry.id().value());
        item.put("type", entry.type().name());
        item.put("source", normalized(entry.source()));
        item.put("schemaVersion", entry.schemaVersion());
        item.put("contentVersion", entry.contentVersion());
        putOptional(item, "status", entry.status());
        putOptional(item, "owningTeam", entry.owningTeam());
        putOptional(item, "revision", entry.revision());
        putOptional(item, "assetId", entry.assetId());
        addStrings(item.putArray("tags"), entry.tags());
        addIds(item.putArray("references"), entry.directReferences());
        addIds(item.putArray("usedBy"), entry.reverseReferences());
        addStrings(item.putArray("localizationLocales"), entry.localizationLocales());
        addIds(item.putArray("aliases"), entry.aliases());
        return item;
    }

    public ObjectNode references(ContentCatalogEntry entry) {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", entry.id().value());
        addIds(root.putArray("references"), entry.directReferences());
        addIds(root.putArray("usedBy"), entry.reverseReferences());
        return root;
    }

    public ObjectNode completions(ContentCatalog catalog) {
        ObjectNode root = mapper.createObjectNode();
        root.put("contentVersion", catalog.contentVersion());
        ArrayNode completions = root.putArray("completions");
        for (ContentCatalogEntry entry : catalog.entries()) {
            ObjectNode item = completions.addObject();
            item.put("label", entry.id().value());
            item.put("type", entry.type().name());
            item.put("detail", entry.type().name() + " - " + normalized(entry.source()));
        }
        return root;
    }

    public byte[] bytes(ObjectNode document) throws JsonProcessingException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
    }

    private static void putOptional(ObjectNode object, String field, String value) {
        if (!value.isBlank()) {
            object.put(field, value);
        }
    }

    private static void addIds(ArrayNode output, Iterable<DefinitionId> ids) {
        ids.forEach(id -> output.add(id.value()));
    }

    private static void addStrings(ArrayNode output, Iterable<String> values) {
        values.forEach(output::add);
    }

    private static String normalized(Path path) {
        return path.toString().replace('\\', '/');
    }
}
