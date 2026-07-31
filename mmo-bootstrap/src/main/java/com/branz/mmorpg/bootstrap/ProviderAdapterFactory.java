package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.provider.Provider;
import com.branz.mmorpg.api.provider.ProviderHealth;
import com.branz.mmorpg.api.provider.ProviderRegistry;
import com.branz.mmorpg.api.provider.ProviderRequirement;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.integrations.PluginCapabilityProbe;
import com.branz.mmorpg.integrations.mythicmobs.MythicMobsProviderAdapter;
import com.branz.mmorpg.integrations.oraxen.OraxenProviderAdapter;
import com.branz.mmorpg.integrations.packetevents.PacketEventsProviderAdapter;
import com.branz.mmorpg.integrations.wallet.PinnedWalletProviderAdapter;
import com.branz.mmorpg.integrations.worldguard.WorldGuardProviderAdapter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ProviderAdapterFactory {
    static final String ORAXEN = "oraxen";
    static final String MYTHIC_MOBS = "mythicmobs";
    static final String PACKET_EVENTS = "packetevents";
    static final String WORLD_GUARD = "worldguard";
    static final String WALLET = "wallet";

    ProviderRegistry create(
            ContentSnapshot snapshot,
            PluginCapabilityProbe probe,
            Clock clock,
            Map<String, ProviderRequirement> configuredProviders,
            String walletPluginName) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(probe, "probe");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(configuredProviders, "configuredProviders");
        List<Provider> providers = new ArrayList<>();
        configuredProviders.forEach(
                (key, requirement) ->
                        providers.add(
                                createProvider(
                                        key,
                                        requirement,
                                        snapshot.manifest().providerVersions(),
                                        probe,
                                        clock,
                                        walletPluginName)));
        return ProviderRegistry.of(providers);
    }

    static Map<String, ProviderRequirement> defaultRequirements() {
        LinkedHashMap<String, ProviderRequirement> requirements = new LinkedHashMap<>();
        requirements.put(ORAXEN, ProviderRequirement.OPTIONAL);
        requirements.put(MYTHIC_MOBS, ProviderRequirement.OPTIONAL);
        requirements.put(PACKET_EVENTS, ProviderRequirement.OPTIONAL);
        requirements.put(WORLD_GUARD, ProviderRequirement.OPTIONAL);
        requirements.put(WALLET, ProviderRequirement.OPTIONAL);
        return Map.copyOf(requirements);
    }

    private static Provider createProvider(
            String key,
            ProviderRequirement requirement,
            Map<String, String> pins,
            PluginCapabilityProbe probe,
            Clock clock,
            String walletPluginName) {
        Objects.requireNonNull(requirement, "requirement");
        String expectedVersion = pins.get(key);
        if (expectedVersion == null || expectedVersion.isBlank()) {
            return new MissingPinProvider("configuration." + key, key, requirement, clock);
        }
        return switch (key) {
            case ORAXEN -> new OraxenProviderAdapter(expectedVersion, requirement, probe, clock);
            case MYTHIC_MOBS ->
                    new MythicMobsProviderAdapter(expectedVersion, requirement, probe, clock);
            case PACKET_EVENTS ->
                    new PacketEventsProviderAdapter(expectedVersion, requirement, probe, clock);
            case WORLD_GUARD ->
                    new WorldGuardProviderAdapter(expectedVersion, requirement, probe, clock);
            case WALLET ->
                    new PinnedWalletProviderAdapter(
                            walletPluginName, expectedVersion, requirement, probe, clock);
            default -> throw new IllegalArgumentException("Unknown provider configuration: " + key);
        };
    }

    private record MissingPinProvider(
            String providerId, String pinName, ProviderRequirement requirement, Clock clock)
            implements Provider {
        private MissingPinProvider {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(pinName, "pinName");
            Objects.requireNonNull(requirement, "requirement");
            Objects.requireNonNull(clock, "clock");
        }

        @Override
        public ProviderHealth health() {
            return ProviderHealth.unavailable(
                    "provider version pin is missing from content manifest: " + pinName,
                    clock.instant());
        }
    }
}
