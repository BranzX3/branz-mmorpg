package com.branz.mmorpg.integrations.oraxen;

import com.branz.mmorpg.api.provider.AssetProvider;
import com.branz.mmorpg.api.provider.ProviderRequirement;
import com.branz.mmorpg.api.provider.ResourcePackProvider;
import com.branz.mmorpg.integrations.AbstractPinnedPluginProvider;
import com.branz.mmorpg.integrations.PinnedPluginSpec;
import com.branz.mmorpg.integrations.PluginCapabilityProbe;
import java.time.Clock;

/** Capability-only skeleton; vendor API calls are introduced with the item/pack milestone. */
public final class OraxenProviderAdapter extends AbstractPinnedPluginProvider
        implements AssetProvider, ResourcePackProvider {
    public OraxenProviderAdapter(
            String expectedVersion,
            ProviderRequirement requirement,
            PluginCapabilityProbe probe,
            Clock clock) {
        super(
                new PinnedPluginSpec("asset.oraxen", "Oraxen", expectedVersion, requirement),
                probe,
                clock);
    }
}
