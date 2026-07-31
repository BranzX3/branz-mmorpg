package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.provider.Provider;
import com.branz.mmorpg.api.provider.ProviderHealth;
import com.branz.mmorpg.api.provider.ProviderRegistry;
import com.branz.mmorpg.api.provider.ProviderRequirement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentStartupGateTest {
    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");

    @TempDir Path contentRoot;

    @Test
    void invalidReferenceEntersMaintenanceBeforeSessionsCanActivate() throws Exception {
        writeContent(true);

        StartupDecision decision =
                new ContentStartupGate()
                        .evaluate(contentRoot, snapshot -> ProviderRegistry.empty(), NOW);

        assertEquals(StartupStatus.MAINTENANCE, decision.status());
        assertFalse(decision.acceptsSessions());
        assertTrue(
                decision.reasons().stream()
                        .anyMatch(reason -> reason.contains("CONTENT_REFERENCE_NOT_FOUND")));
    }

    @Test
    void invalidInvariantEntersMaintenanceBeforeSessionsCanActivate() throws Exception {
        writeContent(false);
        Files.writeString(
                contentRoot.resolve("mount.yml"),
                """
                definition_id: mount.horse.invalid
                schema_version: 1
                species: HORSE
                base_stats: {}
                cargo: {}
                permanent_death: true
                mounted_combat: false
                """);

        StartupDecision decision =
                new ContentStartupGate()
                        .evaluate(contentRoot, snapshot -> ProviderRegistry.empty(), NOW);

        assertEquals(StartupStatus.MAINTENANCE, decision.status());
        assertFalse(decision.acceptsSessions());
        assertTrue(
                decision.reasons().stream()
                        .anyMatch(reason -> reason.contains("CONTENT_INVARIANT_VIOLATION")));
    }

    @Test
    void optionalProviderFailureDegradesButRequiredFailureBlocksSessions() throws Exception {
        writeContent(false);
        Provider optional = unavailableProvider("packets", ProviderRequirement.OPTIONAL);
        Provider required = unavailableProvider("wallet", ProviderRequirement.REQUIRED);

        StartupDecision degraded =
                new ContentStartupGate()
                        .evaluate(
                                contentRoot,
                                snapshot -> ProviderRegistry.of(List.of(optional)),
                                NOW);
        StartupDecision maintenance =
                new ContentStartupGate()
                        .evaluate(
                                contentRoot,
                                snapshot -> ProviderRegistry.of(List.of(required)),
                                NOW);

        assertEquals(StartupStatus.DEGRADED, degraded.status());
        assertTrue(degraded.acceptsSessions());
        assertEquals(StartupStatus.MAINTENANCE, maintenance.status());
        assertFalse(maintenance.acceptsSessions());
    }

    @Test
    void loaderExceptionFailsClosedWithoutLeakingItsMessage() {
        ContentStartupGate gate =
                new ContentStartupGate(
                        root -> {
                            throw new IllegalStateException("sensitive path");
                        });

        StartupDecision decision =
                gate.evaluate(contentRoot, snapshot -> ProviderRegistry.empty(), NOW);

        assertEquals(StartupStatus.MAINTENANCE, decision.status());
        assertEquals(List.of("Content loading failed: IllegalStateException"), decision.reasons());
    }

    private void writeContent(boolean missingReference) throws Exception {
        Files.writeString(
                contentRoot.resolve("content-manifest.json"),
                """
                {
                  "contentVersion": "v1.startup.test",
                  "schemaVersion": 1,
                  "pluginCompatibility": ">=1.0.0 <2.0.0",
                  "minecraftVersion": "26.2",
                  "resourcePackSha256": "TEST",
                  "contentBundleSha256": "TEST",
                  "gitCommit": "TEST",
                  "providerVersions": {},
                  "definitions": {}
                }
                """);
        Files.writeString(
                contentRoot.resolve("material.yml"),
                """
                definition_id: material.iron_ore
                schema_version: 1
                asset_id: material.iron_ore
                item_class: STACKABLE_LOT
                """);
        if (missingReference) {
            Files.writeString(
                    contentRoot.resolve("node.yml"),
                    """
                    definition_id: node.frostpeak.invalid
                    schema_version: 1
                    node_type: COMMON
                    action_ticks: 50
                    commit_tick: 36
                    recovery_seconds: 180
                    base_yields:
                      - item: material.missing
                    """);
        }
    }

    private static Provider unavailableProvider(String id, ProviderRequirement requirement) {
        return new Provider() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public ProviderRequirement requirement() {
                return requirement;
            }

            @Override
            public ProviderHealth health() {
                return ProviderHealth.unavailable("not available", NOW);
            }
        };
    }
}
