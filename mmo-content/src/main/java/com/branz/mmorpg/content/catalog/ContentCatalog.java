package com.branz.mmorpg.content.catalog;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.IdentifierErrorCode;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.reference.ContentReference;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class ContentCatalog {
    private final String contentVersion;
    private final Map<DefinitionId, ContentCatalogEntry> entries;

    private ContentCatalog(String contentVersion, Map<DefinitionId, ContentCatalogEntry> entries) {
        this.contentVersion = contentVersion;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public static ContentCatalog from(ContentSnapshot snapshot) {
        LinkedHashMap<DefinitionId, ContentCatalogEntry> entries = new LinkedHashMap<>();
        snapshot.definitions().all().stream()
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .forEach(definition -> entries.put(definition.id(), toEntry(snapshot, definition)));
        return new ContentCatalog(snapshot.manifest().contentVersion(), entries);
    }

    public String contentVersion() {
        return contentVersion;
    }

    public Collection<ContentCatalogEntry> entries() {
        return entries.values();
    }

    public Optional<ContentCatalogEntry> find(DefinitionId id) {
        return Optional.ofNullable(entries.get(id));
    }

    public List<ContentCatalogEntry> search(String query) {
        String normalized = query.toLowerCase(Locale.ROOT).trim();
        if (normalized.isEmpty()) {
            return List.copyOf(entries.values());
        }
        return entries.values().stream()
                .filter(entry -> searchableText(entry).contains(normalized))
                .toList();
    }

    private static ContentCatalogEntry toEntry(
            ContentSnapshot snapshot, ContentDefinition definition) {
        JsonNode body = definition.body();
        List<DefinitionId> direct =
                snapshot.references().outgoing(definition.id()).stream()
                        .map(ContentReference::targetId)
                        .distinct()
                        .sorted()
                        .toList();
        List<DefinitionId> reverse =
                snapshot.references().incoming(definition.id()).stream()
                        .map(ContentReference::sourceId)
                        .distinct()
                        .sorted()
                        .toList();
        return new ContentCatalogEntry(
                definition.id(),
                definition.type(),
                definition.source(),
                definition.schemaVersion(),
                snapshot.manifest().contentVersion(),
                body.path("status").asText(""),
                body.path("owning_team").asText(""),
                body.path("revision").asText(""),
                body.path("asset_id").asText(""),
                collectTags(body),
                collectAllText(body),
                direct,
                reverse,
                collectTextValues(body.path("localization")),
                collectIds(body.path("aliases")));
    }

    private static Set<String> collectTags(JsonNode body) {
        TreeSet<String> tags = new TreeSet<>();
        collectTagsRecursive(body, "", tags);
        return Collections.unmodifiableSet(tags);
    }

    private static void collectTagsRecursive(JsonNode node, String fieldName, Set<String> tags) {
        if (node.isArray() && (fieldName.equals("tags") || fieldName.endsWith("_tags"))) {
            node.forEach(
                    value -> {
                        if (value.isTextual()) {
                            tags.add(value.asText());
                        }
                    });
            return;
        }
        if (node.isObject()) {
            node.properties()
                    .forEach(entry -> collectTagsRecursive(entry.getValue(), entry.getKey(), tags));
        } else if (node.isArray()) {
            node.forEach(value -> collectTagsRecursive(value, fieldName, tags));
        }
    }

    private static Set<String> collectTextValues(JsonNode node) {
        if (!node.isObject()) {
            return Set.of();
        }
        TreeSet<String> values = new TreeSet<>();
        node.propertyStream().map(Map.Entry::getKey).forEach(values::add);
        return Collections.unmodifiableSet(values);
    }

    private static Set<String> collectAllText(JsonNode node) {
        TreeSet<String> values = new TreeSet<>();
        collectAllTextRecursive(node, values);
        return Collections.unmodifiableSet(values);
    }

    private static void collectAllTextRecursive(JsonNode node, Set<String> values) {
        if (node.isTextual()) {
            values.add(node.asText());
        } else if (node.isContainerNode()) {
            node.valueStream().forEach(child -> collectAllTextRecursive(child, values));
        }
    }

    private static List<DefinitionId> collectIds(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        LinkedHashSet<DefinitionId> ids = new LinkedHashSet<>();
        node.forEach(
                value -> {
                    if (value.isTextual()) {
                        Result<DefinitionId, IdentifierErrorCode> parsed =
                                DefinitionId.parse(value.asText());
                        if (parsed
                                instanceof
                                Result.Success<DefinitionId, IdentifierErrorCode> success) {
                            ids.add(success.value());
                        }
                    }
                });
        return ids.stream().sorted().toList();
    }

    private static String searchableText(ContentCatalogEntry entry) {
        List<String> values = new ArrayList<>();
        values.add(entry.id().value());
        values.add(entry.type().name());
        values.add(entry.source().toString());
        values.add(entry.status());
        values.add(entry.owningTeam());
        values.add(entry.assetId());
        values.addAll(entry.tags());
        values.addAll(entry.searchTerms());
        values.addAll(entry.localizationLocales());
        return String.join(" ", values).toLowerCase(Locale.ROOT);
    }
}
