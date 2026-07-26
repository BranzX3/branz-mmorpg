package com.branz.mmorpg.core.telemetry;

import com.branz.mmorpg.api.telemetry.TelemetryService;
import com.branz.mmorpg.api.telemetry.TelemetrySnapshot;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.LongAdder;

/** Bounded-cardinality local telemetry; metric keys cannot contain payload data. */
public final class InMemoryTelemetryService implements TelemetryService {
    private static final int MAXIMUM_METRICS = 256;
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Map<String, DoubleAccumulator> observations = new ConcurrentHashMap<>();

    @Override public void increment(String metric) { add(metric, 1); }

    @Override public void add(String metric, long amount) {
        if (amount < 0) throw new IllegalArgumentException("counter amount cannot be negative");
        counters.computeIfAbsent(key(metric), ignored -> {
            capacity(counters.size() + observations.size());
            return new LongAdder();
        }).add(amount);
    }

    @Override public void observe(String metric, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("observation must be finite and non-negative");
        }
        observations.computeIfAbsent(key(metric), ignored -> {
            capacity(counters.size() + observations.size());
            return new DoubleAccumulator(Double::max, 0);
        }).accumulate(value);
    }

    @Override public TelemetrySnapshot snapshot() {
        Map<String, Long> counterValues = counters.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> entry.getValue().sum()));
        Map<String, Double> observed = observations.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> entry.getValue().get()));
        return new TelemetrySnapshot(counterValues, observed, Instant.now());
    }

    private static String key(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("invalid telemetry metric name");
        }
        return value;
    }

    private static void capacity(int current) {
        if (current >= MAXIMUM_METRICS) {
            throw new IllegalStateException("telemetry metric cardinality limit reached");
        }
    }
}
