package com.branz.mmorpg.api.telemetry;

public interface TelemetryService {
    void increment(String metric);
    void add(String metric, long amount);
    void observe(String metric, double value);
    TelemetrySnapshot snapshot();
}
