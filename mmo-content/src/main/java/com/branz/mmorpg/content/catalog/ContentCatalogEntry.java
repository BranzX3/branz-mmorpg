package com.branz.mmorpg.content.catalog;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.content.schema.DefinitionType;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ContentCatalogEntry(
        DefinitionId id,
        DefinitionType type,
        Path source,
        int schemaVersion,
        String contentVersion,
        String status,
        String owningTeam,
        String revision,
        String assetId,
        Set<String> tags,
        Set<String> searchTerms,
        List<DefinitionId> directReferences,
        List<DefinitionId> reverseReferences,
        Set<String> localizationLocales,
        List<DefinitionId> aliases) {
    public ContentCatalogEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(contentVersion, "contentVersion");
        status = emptyIfNull(status);
        owningTeam = emptyIfNull(owningTeam);
        revision = emptyIfNull(revision);
        assetId = emptyIfNull(assetId);
        tags = Set.copyOf(tags);
        searchTerms = Set.copyOf(searchTerms);
        directReferences = List.copyOf(directReferences);
        reverseReferences = List.copyOf(reverseReferences);
        localizationLocales = Set.copyOf(localizationLocales);
        aliases = List.copyOf(aliases);
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
