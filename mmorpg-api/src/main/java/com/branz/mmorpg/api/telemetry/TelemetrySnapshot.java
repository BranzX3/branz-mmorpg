package com.branz.mmorpg.api.telemetry;

import java.time.Instant;
import java.util.Map;

public record TelemetrySnapshot(
        Map<String, Long> counters,
        Map<String, Double> observations,
        Instant capturedAt) {
    public TelemetrySnapshot {
        counters = Map.copyOf(counters);
        observations = Map.copyOf(observations);
    }
}
