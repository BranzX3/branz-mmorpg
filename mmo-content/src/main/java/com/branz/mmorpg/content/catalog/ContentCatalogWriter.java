package com.branz.mmorpg.content.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ContentCatalogWriter {
    private final ContentCatalogJson json = new ContentCatalogJson();

    public void write(ContentCatalog catalog, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Files.write(
                outputDirectory.resolve("content-catalog.json"), json.bytes(json.catalog(catalog)));
        Files.write(
                outputDirectory.resolve("stable-id-completions.json"),
                json.bytes(json.completions(catalog)));
    }
}
