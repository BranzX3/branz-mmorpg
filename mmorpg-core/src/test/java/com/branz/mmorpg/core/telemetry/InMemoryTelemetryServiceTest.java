package com.branz.mmorpg.core.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InMemoryTelemetryServiceTest {
    @Test
    void aggregatesCountersAndMaximumObservationsWithoutPayloadKeys() {
        InMemoryTelemetryService telemetry = new InMemoryTelemetryService();
        telemetry.increment("skill.usage");
        telemetry.add("skill.usage", 2);
        telemetry.observe("paper.tick_nanos.max", 10);
        telemetry.observe("paper.tick_nanos.max", 5);

        assertEquals(3, telemetry.snapshot().counters().get("skill.usage"));
        assertEquals(10, telemetry.snapshot().observations()
                .get("paper.tick_nanos.max"));
        assertThrows(IllegalArgumentException.class,
                () -> telemetry.increment("player:secret-token"));
    }
}
