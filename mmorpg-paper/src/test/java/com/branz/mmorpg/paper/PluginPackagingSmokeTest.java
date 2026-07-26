package com.branz.mmorpg.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class PluginPackagingSmokeTest {
    @Test
    void pluginMetadataAndReferenceGameplayContentArePackaged() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        assertNotNull(loader.getResource("plugin.yml"));
        assertNotNull(loader.getResource("config.yml"));
        assertNotNull(loader.getResource(
                "quest-content/quests/the_old_seal.yml"));
        assertNotNull(loader.getResource(
                "quest-content/dialogues/keeper_warning.yml"));
        assertNotNull(loader.getResource(
                "quest-content/cutscenes/seal_opening.yml"));
        String plugin = new String(loader.getResourceAsStream("plugin.yml")
                .readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(plugin.contains("main: com.branz.mmorpg.paper.BranzMMORPGPlugin"));
        assertTrue(plugin.contains("BranzWallet"));
    }

    @Test
    void mmorpgMigrationsNeverCreateCurrencyOwnershipTables() throws Exception {
        var roots = java.nio.file.Path.of("..").toAbsolutePath().normalize();
        try (var paths = java.nio.file.Files.walk(roots)) {
            var migrations = paths.filter(path -> path.toString().replace('\\', '/')
                            .contains("src/main/resources/db/migration"))
                    .filter(path -> path.toString().endsWith(".sql")).toList();
            for (var migration : migrations) {
                String sql = java.nio.file.Files.readString(migration)
                        .toLowerCase(java.util.Locale.ROOT);
                assertFalse(sql.matches("(?s).*create\\s+table\\s+[^;]*(currency|balance|ledger).*"),
                        () -> "MMORPG must not own currency: " + migration);
            }
        }
    }
}
