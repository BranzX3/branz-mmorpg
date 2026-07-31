package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.provider.ProviderReadiness;
import com.branz.mmorpg.api.provider.ProviderRegistry;
import com.branz.mmorpg.api.provider.ProviderRequirement;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.snapshot.ContentLoadFailure;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.content.snapshot.ContentSnapshotLoader;
import com.branz.mmorpg.integrations.PluginCapability;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProviderAdapterFactoryTest {
    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");

    @TempDir Path contentRoot;

    @Test
    void createsAllTypedAdaptersAndAcceptsOnlyExactManifestPins() throws Exception {
        ContentSnapshot snapshot = loadSnapshot(true);
        ProviderRegistry exact =
                new ProviderAdapterFactory()
                        .create(
                                snapshot,
                                pluginName ->
                                        PluginCapability.installed(versionFor(pluginName), true),
                                Clock.fixed(NOW, ZoneOffset.UTC),
                                ProviderAdapterFactory.defaultRequirements(),
                                "Wallet");
        ProviderRegistry mismatch =
                new ProviderAdapterFactory()
                        .create(
                                snapshot,
                                pluginName -> PluginCapability.installed("wrong", true),
                                Clock.fixed(NOW, ZoneOffset.UTC),
                                Map.of(ProviderAdapterFactory.WALLET, ProviderRequirement.REQUIRED),
                                "Wallet");

        assertEquals(5, exact.providers().size());
        assertEquals(ProviderReadiness.READY, exact.healthReport(NOW).readiness());
        assertEquals(ProviderReadiness.MAINTENANCE, mismatch.healthReport(NOW).readiness());
    }

    @Test
    void missingRequiredPinFailsClosed() throws Exception {
        ContentSnapshot snapshot = loadSnapshot(false);
        ProviderRegistry registry =
                new ProviderAdapterFactory()
                        .create(
                                snapshot,
                                pluginName -> PluginCapability.missing(),
                                Clock.fixed(NOW, ZoneOffset.UTC),
                                Map.of(ProviderAdapterFactory.WALLET, ProviderRequirement.REQUIRED),
                                "Wallet");

        assertEquals(ProviderReadiness.MAINTENANCE, registry.healthReport(NOW).readiness());
        assertTrue(registry.healthReport(NOW).providers().getFirst().message().contains("pin"));
    }

    private ContentSnapshot loadSnapshot(boolean includeWallet) throws Exception {
        String walletPin = includeWallet ? ",\n    \"wallet\": \"3.4.5\"" : "";
        Files.writeString(
                contentRoot.resolve("content-manifest.json"),
                """
                {
                  "contentVersion": "v1.provider.test",
                  "schemaVersion": 1,
                  "pluginCompatibility": ">=1.0.0 <2.0.0",
                  "minecraftVersion": "26.2",
                  "resourcePackSha256": "TEST",
                  "contentBundleSha256": "TEST",
                  "gitCommit": "TEST",
                  "providerVersions": {
                    "oraxen": "1.2.3",
                    "mythicmobs": "5.6.7",
                    "packetevents": "2.3.4",
                    "worldguard": "7.8.9"%s
                  },
                  "definitions": {}
                }
                """
                        .formatted(walletPin));
        Result<ContentSnapshot, ContentLoadFailure> loaded =
                new ContentSnapshotLoader().load(contentRoot);
        assertTrue(loaded.isSuccess());
        return ((Result.Success<ContentSnapshot, ContentLoadFailure>) loaded).value();
    }

    private static String versionFor(String pluginName) {
        return switch (pluginName) {
            case "Oraxen" -> "1.2.3";
            case "MythicMobs" -> "5.6.7";
            case "packetevents" -> "2.3.4";
            case "WorldGuard" -> "7.8.9";
            case "Wallet" -> "3.4.5";
            default -> throw new IllegalArgumentException(pluginName);
        };
    }
}
