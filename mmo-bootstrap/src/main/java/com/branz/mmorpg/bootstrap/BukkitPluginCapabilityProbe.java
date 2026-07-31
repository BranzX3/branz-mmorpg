package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.integrations.PluginCapability;
import com.branz.mmorpg.integrations.PluginCapabilityProbe;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

final class BukkitPluginCapabilityProbe {
    private BukkitPluginCapabilityProbe() {}

    static PluginCapabilityProbe capture(
            PluginManager pluginManager, Collection<String> pluginNames) {
        Objects.requireNonNull(pluginManager, "pluginManager");
        LinkedHashMap<String, PluginCapability> capabilities = new LinkedHashMap<>();
        for (String pluginName : pluginNames) {
            Plugin plugin = pluginManager.getPlugin(pluginName);
            PluginCapability capability =
                    plugin == null
                            ? PluginCapability.missing()
                            : PluginCapability.installed(
                                    plugin.getPluginMeta().getVersion(),
                                    pluginManager.isPluginEnabled(plugin));
            capabilities.put(pluginName, capability);
        }
        Map<String, PluginCapability> snapshot = Map.copyOf(capabilities);
        return pluginName -> snapshot.getOrDefault(pluginName, PluginCapability.missing());
    }
}
