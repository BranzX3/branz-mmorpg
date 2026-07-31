package com.branz.mmorpg.content.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentCliTest {
    @TempDir Path contentRoot;

    @Test
    void validatesManifestFixture() throws URISyntaxException {
        ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream standardError = new ByteArrayOutputStream();
        Path fixture =
                Path.of(getClass().getResource("/fixtures/content-manifest.valid.json").toURI());

        int exitCode =
                ContentCli.execute(
                        new String[] {"validate", fixture.toString()},
                        new PrintStream(standardOutput, true, StandardCharsets.UTF_8),
                        new PrintStream(standardError, true, StandardCharsets.UTF_8));

        assertEquals(0, exitCode);
        assertTrue(
                standardOutput.toString(StandardCharsets.UTF_8).contains("Valid content manifest"));
        assertEquals("", standardError.toString(StandardCharsets.UTF_8));
    }

    @Test
    void validatesDirectoryAndPrintsReverseReferences() throws Exception {
        write(
                "content-manifest.json",
                """
                {
                  "contentVersion": "v1.test.1",
                  "schemaVersion": 1,
                  "pluginCompatibility": ">=1.0.0 <2.0.0",
                  "minecraftVersion": "26.2",
                  "resourcePackSha256": "TEST",
                  "contentBundleSha256": "TEST",
                  "gitCommit": "TEST",
                  "definitions": {}
                }
                """);
        write(
                "material.yml",
                """
                definition_id: material.iron_ore
                schema_version: 1
                asset_id: material.iron_ore
                item_class: STACKABLE_LOT
                """);
        write(
                "node.yml",
                """
                definition_id: node.frostpeak.iron_common
                schema_version: 1
                node_type: COMMON
                action_ticks: 50
                commit_tick: 36
                recovery_seconds: 180
                base_yields:
                  - item: material.iron_ore
                """);
        ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream standardError = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(standardOutput, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(standardError, true, StandardCharsets.UTF_8);

        int validateExit =
                ContentCli.execute(new String[] {"validate", contentRoot.toString()}, out, err);
        int referencesExit =
                ContentCli.execute(
                        new String[] {"references", "material.iron_ore", contentRoot.toString()},
                        out,
                        err);
        int searchExit =
                ContentCli.execute(
                        new String[] {"search", "iron", contentRoot.toString()}, out, err);
        Path catalogOutput = contentRoot.resolve("catalog-output");
        int catalogExit =
                ContentCli.execute(
                        new String[] {"catalog", contentRoot.toString(), catalogOutput.toString()},
                        out,
                        err);

        assertEquals(0, validateExit);
        assertEquals(0, referencesExit);
        assertEquals(0, searchExit);
        assertEquals(0, catalogExit);
        String output = standardOutput.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("definitions=2 references=1"));
        assertTrue(output.contains("used by:"));
        assertTrue(output.contains("node.frostpeak.iron_common"));
        assertTrue(output.contains("2 result(s)"));
        assertTrue(Files.isRegularFile(catalogOutput.resolve("content-catalog.json")));
        assertTrue(Files.isRegularFile(catalogOutput.resolve("stable-id-completions.json")));
        assertEquals("", standardError.toString(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsInvalidCatalogServerPortWithoutLoadingContent() {
        ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream standardError = new ByteArrayOutputStream();

        int exitCode =
                ContentCli.execute(
                        new String[] {"serve-catalog", contentRoot.toString(), "not-a-port"},
                        new PrintStream(standardOutput, true, StandardCharsets.UTF_8),
                        new PrintStream(standardError, true, StandardCharsets.UTF_8));

        assertEquals(2, exitCode);
        assertEquals("", standardOutput.toString(StandardCharsets.UTF_8));
        assertTrue(
                standardError.toString(StandardCharsets.UTF_8).contains("Port must be an integer"));
    }

    private void write(String relativePath, String content) throws Exception {
        Path target = contentRoot.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
