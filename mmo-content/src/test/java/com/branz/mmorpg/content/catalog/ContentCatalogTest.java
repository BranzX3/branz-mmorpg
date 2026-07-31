package com.branz.mmorpg.content.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.snapshot.ContentLoadFailure;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.content.snapshot.ContentSnapshotLoader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentCatalogTest {
    @TempDir Path outputDirectory;

    @Test
    void indexesSearchTextReferencesAndStableIdCompletions() throws Exception {
        ContentSnapshot snapshot = loadFixture();

        ContentCatalog catalog = ContentCatalog.from(snapshot);
        new ContentCatalogWriter().write(catalog, outputDirectory);

        assertEquals(2, catalog.entries().size());
        assertEquals(
                DefinitionId.of("node.frostpeak.iron_common"),
                catalog.find(DefinitionId.of("material.iron_ore"))
                        .orElseThrow()
                        .reverseReferences()
                        .getFirst());
        assertEquals(
                DefinitionId.of("material.iron_ore"),
                catalog.search("stackable_lot").getFirst().id());
        assertEquals(
                DefinitionId.of("node.frostpeak.iron_common"),
                catalog.search("lifeskill_node").getFirst().id());

        String completions =
                Files.readString(outputDirectory.resolve("stable-id-completions.json"));
        String exportedCatalog = Files.readString(outputDirectory.resolve("content-catalog.json"));
        assertTrue(completions.contains("\"label\" : \"material.iron_ore\""));
        assertTrue(exportedCatalog.contains("\"usedBy\""));
        assertTrue(
                exportedCatalog.indexOf("material.iron_ore")
                        < exportedCatalog.indexOf("node.frostpeak.iron_common"));
    }

    private ContentSnapshot loadFixture() throws URISyntaxException {
        Path fixture = Path.of(getClass().getResource("/fixtures/catalog-valid").toURI());
        Result<ContentSnapshot, ContentLoadFailure> loaded =
                new ContentSnapshotLoader().load(fixture);
        assertTrue(loaded.isSuccess());
        return ((Result.Success<ContentSnapshot, ContentLoadFailure>) loaded).value();
    }
}
