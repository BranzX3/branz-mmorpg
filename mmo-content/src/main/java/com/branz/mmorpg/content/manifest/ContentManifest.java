package com.branz.mmorpg.content.manifest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable metadata for one compiled content artifact. */
public record ContentManifest(
        String contentVersion,
        int schemaVersion,
        String pluginCompatibility,
        String minecraftVersion,
        String resourcePackSha256,
        String contentBundleSha256,
        String gitCommit,
        Map<String, Integer> definitions) {
    public ContentManifest {
        contentVersion = requireText(contentVersion, "contentVersion");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        pluginCompatibility = requireText(pluginCompatibility, "pluginCompatibility");
        minecraftVersion = requireText(minecraftVersion, "minecraftVersion");
        resourcePackSha256 = requireText(resourcePackSha256, "resourcePackSha256");
        contentBundleSha256 = requireText(contentBundleSha256, "contentBundleSha256");
        gitCommit = requireText(gitCommit, "gitCommit");
        Objects.requireNonNull(definitions, "definitions");
        LinkedHashMap<String, Integer> copy = new LinkedHashMap<>();
        definitions.forEach(
                (name, count) -> {
                    String checkedName = requireText(name, "definition category");
                    if (count == null || count < 0) {
                        throw new IllegalArgumentException(
                                "Definition count for " + checkedName + " must be non-negative");
                    }
                    copy.put(checkedName, count);
                });
        definitions = Collections.unmodifiableMap(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
