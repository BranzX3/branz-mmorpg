package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.provider.ProviderHealthReport;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

record StartupDecision(
        StartupStatus status,
        Optional<ContentSnapshot> snapshot,
        ProviderHealthReport providerHealth,
        List<String> reasons) {
    StartupDecision {
        Objects.requireNonNull(status, "status");
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(providerHealth, "providerHealth");
        reasons = List.copyOf(reasons);
        if (status != StartupStatus.MAINTENANCE && snapshot.isEmpty()) {
            throw new IllegalArgumentException("An active startup requires a content snapshot");
        }
    }

    boolean acceptsSessions() {
        return status != StartupStatus.MAINTENANCE;
    }
}
