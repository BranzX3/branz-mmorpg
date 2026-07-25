package com.branz.mmorpg.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.service.ServiceState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceContainerTest {

    @Test
    void startsInOrderAndStopsInReverse() {
        List<String> log = new ArrayList<>();
        ServiceContainer container = new ServiceContainer();
        container.register(new RecordingService("first", log, false));
        container.register(new RecordingService("second", log, false));

        container.startAll();
        assertTrue(container.ready());
        assertEquals(List.of("start:first", "start:second"), log);

        container.stopAll();
        assertFalse(container.ready());
        assertEquals(List.of("start:first", "start:second", "stop:second", "stop:first"), log);
    }

    @Test
    void failureInRequiredServicePreventsReady() {
        List<String> log = new ArrayList<>();
        ServiceContainer container = new ServiceContainer();
        RecordingService healthy = container.register(new RecordingService("healthy", log, false));
        container.register(new RecordingService("broken", log, true));

        MMOException failure = assertThrows(MMOException.class, container::startAll);

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, failure.code());
        assertFalse(container.ready());
        assertEquals(ServiceState.STOPPED, healthy.state(), "already-started services are rolled back");
        assertEquals(ServiceState.FAILED, container.status("broken").state());
        assertEquals(List.of("start:healthy", "start:broken", "stop:broken", "stop:healthy"), log);
    }

    @Test
    void healthReportListsDegradedServices() {
        ServiceContainer container = new ServiceContainer();
        container.register(new RecordingService("healthy", new ArrayList<>(), false));
        container.register(new RecordingService("broken", new ArrayList<>(), true));

        assertThrows(MMOException.class, container::startAll);

        assertFalse(container.health().ready());
        assertEquals(2, container.health().degraded().size());
        assertEquals("broken", container.health().degraded().get(1).name());
    }

    @Test
    void startStopCyclesDoNotLeakExecutors() {
        int before = Thread.activeCount();
        for (int i = 0; i < 5; i++) {
            ServiceContainer container = new ServiceContainer();
            container.register(com.branz.mmorpg.core.runtime.ExecutorScheduler.direct(2));
            container.startAll();
            assertTrue(container.ready());
            container.stopAll();
        }
        assertTrue(Thread.activeCount() <= before + 1,
                "scheduler threads outlived their container: " + before + " -> " + Thread.activeCount());
    }

    @Test
    void stopIsIdempotentAndRegistrationAfterStartIsRejected() {
        ServiceContainer container = new ServiceContainer();
        List<String> log = new ArrayList<>();
        container.register(new RecordingService("only", log, false));
        container.startAll();

        container.stopAll();
        container.stopAll();
        assertEquals(1, log.stream().filter(entry -> entry.equals("stop:only")).count());

        assertEquals(ErrorCode.SERVICE_LIFECYCLE,
                assertThrows(MMOException.class,
                        () -> container.register(new RecordingService("late", log, false))).code());
    }

    @Test
    void duplicateServiceNameIsRejected() {
        ServiceContainer container = new ServiceContainer();
        container.register(new RecordingService("dup", new ArrayList<>(), false));
        assertEquals(ErrorCode.INVALID_ARGUMENT,
                assertThrows(MMOException.class,
                        () -> container.register(new RecordingService("dup", new ArrayList<>(), false))).code());
    }

    private static final class RecordingService extends AbstractService {

        private final List<String> log;
        private final boolean failOnStart;

        RecordingService(String name, List<String> log, boolean failOnStart) {
            super(name);
            this.log = log;
            this.failOnStart = failOnStart;
        }

        @Override
        protected void onStart() {
            log.add("start:" + name());
            if (failOnStart) {
                throw new IllegalStateException("boom");
            }
        }

        @Override
        protected void onStop() {
            log.add("stop:" + name());
        }
    }
}
