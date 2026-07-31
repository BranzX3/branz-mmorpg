package com.branz.mmorpg.integrations;

import com.branz.mmorpg.api.provider.Provider;
import com.branz.mmorpg.api.provider.ProviderHealth;
import com.branz.mmorpg.api.provider.ProviderRequirement;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Vendor-neutral adapter skeleton which fails closed on missing, disabled or version-mismatched
 * plugins.
 */
public abstract class AbstractPinnedPluginProvider implements Provider {
    private final PinnedPluginSpec spec;
    private final PluginCapabilityProbe probe;
    private final Clock clock;

    protected AbstractPinnedPluginProvider(
            PinnedPluginSpec spec, PluginCapabilityProbe probe, Clock clock) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public final String providerId() {
        return spec.providerId();
    }

    @Override
    public final ProviderRequirement requirement() {
        return spec.requirement();
    }

    @Override
    public final ProviderHealth health() {
        Instant checkedAt = clock.instant();
        PluginCapability capability;
        try {
            capability = Objects.requireNonNull(probe.probe(spec.pluginName()), "capability");
        } catch (RuntimeException exception) {
            return ProviderHealth.unavailable(
                    "capability probe failed: " + exception.getClass().getSimpleName(), checkedAt);
        }
        if (!capability.installed()) {
            return ProviderHealth.unavailable(
                    "plugin is not installed: " + spec.pluginName(), checkedAt);
        }
        if (!capability.enabled()) {
            return ProviderHealth.unavailable(
                    "plugin is not enabled: " + spec.pluginName(), checkedAt);
        }
        if (!spec.expectedVersion().equals(capability.version())) {
            return ProviderHealth.unavailable(
                    "plugin version mismatch: expected "
                            + spec.expectedVersion()
                            + ", actual "
                            + capability.version(),
                    checkedAt);
        }
        return ProviderHealth.healthy(checkedAt);
    }

    public final PinnedPluginSpec spec() {
        return spec;
    }
}
