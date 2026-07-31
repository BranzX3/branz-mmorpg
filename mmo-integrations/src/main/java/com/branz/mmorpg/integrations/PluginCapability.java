package com.branz.mmorpg.integrations;

/** Runtime facts exposed by the host about one external plugin. */
public record PluginCapability(boolean installed, boolean enabled, String version) {
    public PluginCapability {
        version = version == null ? "" : version;
        if (!installed && enabled) {
            throw new IllegalArgumentException("A missing plugin cannot be enabled");
        }
    }

    public static PluginCapability missing() {
        return new PluginCapability(false, false, "");
    }

    public static PluginCapability installed(String version, boolean enabled) {
        return new PluginCapability(true, enabled, version);
    }
}
