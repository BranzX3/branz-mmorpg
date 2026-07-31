package com.branz.mmorpg.api.provider;

public interface Provider {
    String providerId();

    ProviderRequirement requirement();

    ProviderHealth health();
}
