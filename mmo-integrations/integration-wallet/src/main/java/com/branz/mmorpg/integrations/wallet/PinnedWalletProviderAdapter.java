package com.branz.mmorpg.integrations.wallet;

import com.branz.mmorpg.api.provider.ProviderRequirement;
import com.branz.mmorpg.api.provider.WalletProvider;
import com.branz.mmorpg.integrations.AbstractPinnedPluginProvider;
import com.branz.mmorpg.integrations.PinnedPluginSpec;
import com.branz.mmorpg.integrations.PluginCapabilityProbe;
import java.time.Clock;

/**
 * Generic wallet capability skeleton. Transaction methods are deliberately deferred to Milestone 2,
 * where journal/idempotency semantics are available.
 */
public final class PinnedWalletProviderAdapter extends AbstractPinnedPluginProvider
        implements WalletProvider {
    public PinnedWalletProviderAdapter(
            String pluginName,
            String expectedVersion,
            ProviderRequirement requirement,
            PluginCapabilityProbe probe,
            Clock clock) {
        super(
                new PinnedPluginSpec("wallet.external", pluginName, expectedVersion, requirement),
                probe,
                clock);
    }
}
