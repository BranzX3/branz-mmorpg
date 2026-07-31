package com.branz.mmorpg.integrations.packetevents;

import com.branz.mmorpg.api.provider.PacketProvider;
import com.branz.mmorpg.api.provider.ProviderRequirement;
import com.branz.mmorpg.integrations.AbstractPinnedPluginProvider;
import com.branz.mmorpg.integrations.PinnedPluginSpec;
import com.branz.mmorpg.integrations.PluginCapabilityProbe;
import java.time.Clock;

/** Capability-only skeleton; packet presentation hooks are introduced with Scene/combat tooling. */
public final class PacketEventsProviderAdapter extends AbstractPinnedPluginProvider
        implements PacketProvider {
    public PacketEventsProviderAdapter(
            String expectedVersion,
            ProviderRequirement requirement,
            PluginCapabilityProbe probe,
            Clock clock) {
        super(
                new PinnedPluginSpec(
                        "packet.packetevents", "packetevents", expectedVersion, requirement),
                probe,
                clock);
    }
}
