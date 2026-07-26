package com.branz.mmorpg.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BundledContentTest {
    @Test
    void paperBundleLoadsAsOneValidatedSnapshot() {
        Path content = locateBundle();
        AtomicContentService service = new AtomicContentService();

        var result = service.reload(content);

        assertTrue(result.successful(), () -> String.join("\n", result.diagnostics()));
        assertTrue(service.snapshot().skills().containsKey(ContentId.parse("branz:heavy_slash")));
        assertTrue(service.snapshot().weapons().containsKey(ContentId.parse("branz:broadsword")));
        assertTrue(service.snapshot().lootTables().containsKey(ContentId.parse("branz:aether_cache")));
        assertTrue(service.snapshot().gatheringNodes()
                .containsKey(ContentId.parse("branz:aether_deposit")));
        assertTrue(service.snapshot().professions()
                .containsKey(ContentId.parse("branz:blacksmithing")));
        assertTrue(service.snapshot().recipes()
                .containsKey(ContentId.parse("branz:aether_ingot_recipe")));
        assertTrue(service.snapshot().mobs()
                .containsKey(ContentId.parse("branz:seal_guardian")));
        assertTrue(service.snapshot().encounters()
                .containsKey(ContentId.parse("branz:seal_guardian_encounter")));
        assertTrue(service.snapshot().characterClasses()
                .containsKey(ContentId.parse("branz:warrior")));
        assertTrue(service.snapshot().characterClasses()
                .containsKey(ContentId.parse("branz:mage")));
        assertTrue(service.snapshot().characterClasses()
                .containsKey(ContentId.parse("branz:rogue")));
    }

    private static Path locateBundle() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path candidate : java.util.List.of(
                working.resolve("mmorpg-paper/src/main/resources/content"),
                working.resolve("../mmorpg-paper/src/main/resources/content").normalize())) {
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("Cannot locate bundled Paper content from " + working);
    }
}
