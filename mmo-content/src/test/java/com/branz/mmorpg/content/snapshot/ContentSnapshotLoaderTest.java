package com.branz.mmorpg.content.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.diagnostic.ContentDiagnosticCode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentSnapshotLoaderTest {
    @TempDir Path contentRoot;

    @Test
    void loadsImmutableSnapshotAndBuildsReverseReferences() throws IOException {
        writeManifest();
        write(
                "city.yml",
                """
                definition_id: city.frostpeak
                schema_version: 1
                production_categories: [METAL, STONE, TOOLS]
                import_needs: [TIMBER, HERBS, MEDICINE]
                trade_goods: [trade.frostpeak.steel_crate, trade.frostpeak.tools]
                workshop_specialties: [SMITHING]
                base_demand: {TIMBER: 130}
                """);
        write(
                "trade-steel.yml",
                """
                definition_id: trade.frostpeak.steel_crate
                schema_version: 1
                """);
        write(
                "trade-tools.yml",
                """
                definition_id: trade.frostpeak.tools
                schema_version: 1
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
                "food.yml",
                """
                definition_id: food.worker_ration
                schema_version: 1
                asset_id: food.worker_ration
                item_class: STACKABLE_LOT
                """);
        write(
                "region.yml",
                """
                definition_id: node_region.frostpeak_mines
                schema_version: 1
                """);
        write(
                "worker.yml",
                """
                definition_id: worker_job.frostpeak.iron_ore
                schema_version: 1
                role: GATHERER
                city: city.frostpeak
                required_node_knowledge: node_region.frostpeak_mines
                duration_seconds: 3600
                costs:
                  food_item: food.worker_ration
                outputs:
                  - item: material.iron_ore
                offline_allowed: true
                queue_cap_hours: 24
                """);

        Result<ContentSnapshot, ContentLoadFailure> loaded =
                new ContentSnapshotLoader().load(contentRoot);

        assertTrue(loaded.isSuccess());
        ContentSnapshot snapshot =
                ((Result.Success<ContentSnapshot, ContentLoadFailure>) loaded).value();
        assertEquals(7, snapshot.definitions().size());
        assertEquals(6, snapshot.references().all().size());
        assertEquals(
                DefinitionId.of("worker_job.frostpeak.iron_ore"),
                snapshot.references()
                        .incoming(DefinitionId.of("material.iron_ore"))
                        .getFirst()
                        .sourceId());

        ContentDefinition worker =
                snapshot.definitions()
                        .find(DefinitionId.of("worker_job.frostpeak.iron_ore"))
                        .orElseThrow();
        ((ObjectNode) worker.body()).put("city", "city.changed");
        assertEquals("city.frostpeak", worker.body().path("city").asText());
    }

    @Test
    void missingReferenceFailsBeforeSnapshotActivation() throws IOException {
        writeManifest();
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
                  - item: material.missing
                """);

        Result<ContentSnapshot, ContentLoadFailure> loaded =
                new ContentSnapshotLoader().load(contentRoot);

        assertFalse(loaded.isSuccess());
        assertTrue(
                failure(loaded).diagnostics().stream()
                        .anyMatch(
                                diagnostic ->
                                        diagnostic.code()
                                                        == ContentDiagnosticCode
                                                                .CONTENT_REFERENCE_NOT_FOUND
                                                && diagnostic.line() == 8));
    }

    @Test
    void wrongReferenceTypeHasStableDiagnosticCode() throws IOException {
        writeManifest();
        write(
                "city.yml",
                """
                definition_id: city.frostpeak
                schema_version: 1
                """);
        write(
                "worker.yml",
                """
                definition_id: worker_job.frostpeak.iron_ore
                schema_version: 1
                outputs:
                  - item: city.frostpeak
                """);

        Result<ContentSnapshot, ContentLoadFailure> loaded =
                new ContentSnapshotLoader().load(contentRoot);

        assertFalse(loaded.isSuccess());
        assertTrue(
                failure(loaded).diagnostics().stream()
                        .anyMatch(
                                diagnostic ->
                                        diagnostic.code()
                                                == ContentDiagnosticCode
                                                        .CONTENT_REFERENCE_WRONG_TYPE));
    }

    @Test
    void duplicateDefinitionIdsFailDeterministically() throws IOException {
        writeManifest();
        write(
                "first.yml",
                """
                definition_id: status.burn
                schema_version: 1
                """);
        write(
                "second.yml",
                """
                definition_id: status.burn
                schema_version: 1
                """);

        Result<ContentSnapshot, ContentLoadFailure> loaded =
                new ContentSnapshotLoader().load(contentRoot);

        assertFalse(loaded.isSuccess());
        assertTrue(
                failure(loaded).diagnostics().stream()
                        .anyMatch(
                                diagnostic ->
                                        diagnostic.code()
                                                == ContentDiagnosticCode.CONTENT_ID_DUPLICATE));
    }

    @Test
    void malformedStableIdInReferenceCannotBeSilentlyIgnored() throws IOException {
        writeManifest();
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
                  - item: Iron Ore
                """);

        Result<ContentSnapshot, ContentLoadFailure> loaded =
                new ContentSnapshotLoader().load(contentRoot);

        assertFalse(loaded.isSuccess());
        assertTrue(
                failure(loaded).diagnostics().stream()
                        .anyMatch(
                                diagnostic ->
                                        diagnostic.code()
                                                == ContentDiagnosticCode
                                                        .CONTENT_ID_INVALID_FORMAT));
    }

    @Test
    void documentedFieldRangeFailsBeforeSnapshotActivation() throws IOException {
        writeManifest();
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
                action_ticks: 121
                commit_tick: 36
                recovery_seconds: 180
                base_yields:
                  - item: material.iron_ore
                """);

        Result<ContentSnapshot, ContentLoadFailure> loaded =
                new ContentSnapshotLoader().load(contentRoot);

        assertFalse(loaded.isSuccess());
        assertTrue(
                failure(loaded).diagnostics().stream()
                        .anyMatch(
                                diagnostic ->
                                        diagnostic.code()
                                                == ContentDiagnosticCode
                                                        .CONTENT_SCHEMA_OUT_OF_RANGE));
    }

    private ContentLoadFailure failure(Result<ContentSnapshot, ContentLoadFailure> result) {
        return ((Result.Failure<ContentSnapshot, ContentLoadFailure>) result).error();
    }

    private void writeManifest() throws IOException {
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
    }

    private void write(String relativePath, String content) throws IOException {
        Path target = contentRoot.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
