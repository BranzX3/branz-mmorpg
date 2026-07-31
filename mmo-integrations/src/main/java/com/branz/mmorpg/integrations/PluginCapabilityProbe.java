package com.branz.mmorpg.integrations;

@FunctionalInterface
public interface PluginCapabilityProbe {
    PluginCapability probe(String pluginName);
}
