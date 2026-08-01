package com.branz.mmorpg.lifeskills.progression;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record LifeFocusRuntime(
        int focus, Instant lastRecoveryAt, Map<UUID, Integer> processedWorkOperations) {
    public LifeFocusRuntime {
        if (focus < 0 || focus > LifeFocusEngine.MAXIMUM_FOCUS) {
            throw new IllegalArgumentException("Life Focus must be in [0, 100]");
        }
        Objects.requireNonNull(lastRecoveryAt, "lastRecoveryAt");
        processedWorkOperations =
                Map.copyOf(
                        Objects.requireNonNull(processedWorkOperations, "processedWorkOperations"));
        processedWorkOperations.forEach(
                (operationId, cost) -> {
                    Objects.requireNonNull(operationId, "operationId");
                    if (cost == null || cost < 0 || cost > LifeFocusEngine.MAXIMUM_FOCUSED_COST) {
                        throw new IllegalArgumentException(
                                "processed Focus cost must be in [0, 5]");
                    }
                });
    }

    public static LifeFocusRuntime full(Instant now) {
        return new LifeFocusRuntime(LifeFocusEngine.MAXIMUM_FOCUS, now, Map.of());
    }
}
