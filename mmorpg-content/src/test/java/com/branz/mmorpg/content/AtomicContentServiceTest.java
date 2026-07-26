package com.branz.mmorpg.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicContentServiceTest {
    @TempDir
    Path directory;

    @Test
    void loadsTypedMaterialDefinition() throws IOException {
        write("ore.yml", validMaterial("branz:aether_ore"));
        AtomicContentService service = new AtomicContentService();

        var result = service.reload(directory);

        assertTrue(result.successful());
        assertEquals(1, result.definitionCount());
        assertTrue(service.snapshot().materials().containsKey(ContentId.parse("branz:aether_ore")));
    }

    @Test
    void failedReloadKeepsLastValidSnapshot() throws IOException {
        Path file = write("ore.yml", validMaterial("branz:aether_ore"));
        AtomicContentService service = new AtomicContentService();
        assertTrue(service.reload(directory).successful());
        long validRevision = service.snapshot().revision();
        Files.writeString(file, "type: material\nid: invalid\n");

        var result = service.reload(directory);

        assertFalse(result.successful());
        assertEquals(validRevision, service.snapshot().revision());
        assertEquals(1, service.snapshot().definitions().size());
    }

    @Test
    void rejectsDuplicateIds() throws IOException {
        write("one.yml", validMaterial("branz:aether_ore"));
        write("two.yml", validMaterial("branz:aether_ore"));

        var result = new AtomicContentService().reload(directory);

        assertFalse(result.successful());
        assertTrue(result.diagnostics().stream().anyMatch(line -> line.contains("duplicate content ID")));
    }

    private Path write(String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private String validMaterial(String id) {
        return """
                type: material
                id: %s
                display-name: Aether Ore
                category: crafting_material
                rarity: uncommon
                tradable: true
                max-stack-size: 64
                """.formatted(id);
    }
}
