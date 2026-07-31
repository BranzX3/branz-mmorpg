package com.branz.mmorpg.integrations.mythicmobs;

import com.branz.mmorpg.api.provider.MobProvider;
import com.branz.mmorpg.api.provider.ProviderRequirement;
import com.branz.mmorpg.integrations.AbstractPinnedPluginProvider;
import com.branz.mmorpg.integrations.PinnedPluginSpec;
import com.branz.mmorpg.integrations.PluginCapabilityProbe;
import java.time.Clock;

/** Capability-only skeleton; mob spawning calls are introduced with encounter runtime. */
public final class MythicMobsProviderAdapter extends AbstractPinnedPluginProvider
        implements MobProvider {
    public MythicMobsProviderAdapter(
            String expectedVersion,
            ProviderRequirement requirement,
            PluginCapabilityProbe probe,
            Clock clock) {
        super(
                new PinnedPluginSpec("mob.mythicmobs", "MythicMobs", expectedVersion, requirement),
                probe,
                clock);
    }
}
