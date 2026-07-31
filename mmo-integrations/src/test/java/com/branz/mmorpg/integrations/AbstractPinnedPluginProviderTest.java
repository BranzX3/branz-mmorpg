package com.branz.mmorpg.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.provider.ProviderHealth;
import com.branz.mmorpg.api.provider.ProviderRequirement;
import com.branz.mmorpg.api.provider.ProviderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AbstractPinnedPluginProviderTest {
    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final PinnedPluginSpec SPEC =
            new PinnedPluginSpec("asset.oraxen", "Oraxen", "1.2.3", ProviderRequirement.OPTIONAL);

    @Test
    void isHealthyOnlyForTheExactEnabledPin() {
        TestProvider provider =
                new TestProvider(pluginName -> PluginCapability.installed("1.2.3", true));

        ProviderHealth health = provider.health();

        assertEquals(ProviderStatus.HEALTHY, health.status());
        assertEquals(NOW, health.checkedAt());
    }

    @Test
    void reportsMissingDisabledAndVersionMismatchAsUnavailable() {
        assertUnavailable(pluginName -> PluginCapability.missing(), "not installed");
        assertUnavailable(pluginName -> PluginCapability.installed("1.2.3", false), "not enabled");
        assertUnavailable(
                pluginName -> PluginCapability.installed("9.9.9", true), "version mismatch");
    }

    @Test
    void rejectsAnEmptyVersionPin() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PinnedPluginSpec(
                                "asset.oraxen", "Oraxen", " ", ProviderRequirement.OPTIONAL));
    }

    private static void assertUnavailable(PluginCapabilityProbe probe, String message) {
        ProviderHealth health = new TestProvider(probe).health();
        assertEquals(ProviderStatus.UNAVAILABLE, health.status());
        org.junit.jupiter.api.Assertions.assertTrue(health.message().contains(message));
    }

    private static final class TestProvider extends AbstractPinnedPluginProvider {
        private TestProvider(PluginCapabilityProbe probe) {
            super(SPEC, probe, CLOCK);
        }
    }
}
