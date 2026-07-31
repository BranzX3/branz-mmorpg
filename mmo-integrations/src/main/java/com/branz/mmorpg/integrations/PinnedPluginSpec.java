package com.branz.mmorpg.integrations;

import com.branz.mmorpg.api.provider.ProviderRequirement;
import java.util.Objects;

public record PinnedPluginSpec(
        String providerId,
        String pluginName,
        String expectedVersion,
        ProviderRequirement requirement) {
    public PinnedPluginSpec {
        providerId = requireText(providerId, "providerId");
        pluginName = requireText(pluginName, "pluginName");
        expectedVersion = requireText(expectedVersion, "expectedVersion");
        Objects.requireNonNull(requirement, "requirement");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
