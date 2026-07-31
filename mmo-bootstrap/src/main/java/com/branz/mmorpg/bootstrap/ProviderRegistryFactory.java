package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.provider.ProviderRegistry;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;

@FunctionalInterface
interface ProviderRegistryFactory {
    ProviderRegistry create(ContentSnapshot snapshot);
}
