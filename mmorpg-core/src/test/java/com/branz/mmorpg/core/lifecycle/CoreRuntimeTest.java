package com.branz.mmorpg.core.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.lifecycle.ServiceState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreRuntimeTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void startsAndStopsRepeatedlyInOrder() {
        RecordingService first = new RecordingService("first", true, false);
        RecordingService second = new RecordingService("second", true, false);
        CoreRuntime runtime = new CoreRuntime(List.of(first, second), CLOCK);

        runtime.start();
        assertEquals(ServiceState.READY, runtime.health().state());
        runtime.stop();
        runtime.start();
        runtime.stop();

        assertEquals(ServiceState.STOPPED, runtime.health().state());
        assertEquals(2, first.startCount);
        assertEquals(2, first.stopCount);
        assertEquals(2, second.startCount);
        assertEquals(2, second.stopCount);
    }

    @Test
    void requiredFailurePreventsReadyAndRollsBackStartedServices() {
        RecordingService started = new RecordingService("started", true, false);
        RecordingService failed = new RecordingService("failed", true, true);
        CoreRuntime runtime = new CoreRuntime(List.of(started, failed), CLOCK);

        assertThrows(CoreLifecycleException.class, runtime::start);

        assertEquals(ServiceState.FAILED, runtime.health().state());
        assertEquals(1, started.stopCount);
        assertEquals(ServiceState.STOPPED, runtime.health().components().get(0).state());
        assertEquals(ServiceState.FAILED, runtime.health().components().get(1).state());
    }

    @Test
    void optionalFailureKeepsRuntimeReadyButReportsComponentFailure() {
        RecordingService optional = new RecordingService("optional", false, true);
        CoreRuntime runtime = new CoreRuntime(List.of(optional), CLOCK);

        runtime.start();

        assertEquals(ServiceState.READY, runtime.health().state());
        assertEquals(ServiceState.FAILED, runtime.health().components().getFirst().state());
    }

    @Test
    void duplicateServiceNamesAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CoreRuntime(
                        List.of(
                                new RecordingService("same", true, false),
                                new RecordingService("same", false, false)),
                        CLOCK));
    }

    private static final class RecordingService implements ManagedService {
        private final String name;
        private final boolean required;
        private final boolean failOnStart;
        private int startCount;
        private int stopCount;

        private RecordingService(String name, boolean required, boolean failOnStart) {
            this.name = name;
            this.required = required;
            this.failOnStart = failOnStart;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean required() {
            return required;
        }

        @Override
        public void start() {
            startCount++;
            if (failOnStart) {
                throw new IllegalStateException("boom");
            }
        }

        @Override
        public void stop() {
            stopCount++;
        }
    }
}
