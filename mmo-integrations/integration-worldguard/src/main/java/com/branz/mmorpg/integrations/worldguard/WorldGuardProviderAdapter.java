package com.branz.mmorpg.integrations.worldguard;

import com.branz.mmorpg.api.provider.ProviderRequirement;
import com.branz.mmorpg.api.provider.RegionProvider;
import com.branz.mmorpg.integrations.AbstractPinnedPluginProvider;
import com.branz.mmorpg.integrations.PinnedPluginSpec;
import com.branz.mmorpg.integrations.PluginCapabilityProbe;
import java.time.Clock;

/** Capability-only skeleton; region rule mapping is introduced with world-loop ownership. */
public final class WorldGuardProviderAdapter extends AbstractPinnedPluginProvider
        implements RegionProvider {
    public WorldGuardProviderAdapter(
            String expectedVersion,
            ProviderRequirement requirement,
            PluginCapabilityProbe probe,
            Clock clock) {
        super(
                new PinnedPluginSpec(
                        "region.worldguard", "WorldGuard", expectedVersion, requirement),
                probe,
                clock);
    }
}
