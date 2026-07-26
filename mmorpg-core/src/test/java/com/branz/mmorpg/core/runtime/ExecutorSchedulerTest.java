package com.branz.mmorpg.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExecutorSchedulerTest {

    @Test
    void runsAsyncWorkOffTheCallingThread() throws Exception {
        ExecutorScheduler scheduler = ExecutorScheduler.direct(2);
        scheduler.start();
        try {
            String thread = scheduler.async(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS);
            assertEquals("branz-mmorpg-async", thread);
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void syncWorkRunsOnTheSuppliedExecutor() throws Exception {
        AtomicInteger onOwningThread = new AtomicInteger();
        ExecutorScheduler scheduler = new ExecutorScheduler(1, runnable -> {
            onOwningThread.incrementAndGet();
            runnable.run();
        });
        scheduler.start();
        try {
            scheduler.sync(() -> { }).get(5, TimeUnit.SECONDS);
            assertEquals(1, onOwningThread.get());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void delayedWorkCompletes() throws Exception {
        ExecutorScheduler scheduler = ExecutorScheduler.direct(1);
        scheduler.start();
        try {
            AtomicInteger ran = new AtomicInteger();
            scheduler.syncLater(ran::incrementAndGet, Duration.ofMillis(10)).get(5, TimeUnit.SECONDS);
            assertEquals(1, ran.get());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void refusesNewWorkAfterShutdown() {
        ExecutorScheduler scheduler = ExecutorScheduler.direct(1);
        scheduler.start();
        assertTrue(scheduler.drainAndShutdown(Duration.ofSeconds(5)));

        MMOException refused = assertThrows(MMOException.class, () -> scheduler.async(() -> "nope"));
        assertEquals(ErrorCode.SHUTTING_DOWN, refused.code());
        scheduler.stop();
    }

    @Test
    void asyncFailurePropagatesToTheCaller() {
        ExecutorScheduler scheduler = ExecutorScheduler.direct(1);
        scheduler.start();
        try {
            ExecutionException thrown = assertThrows(ExecutionException.class,
                    () -> scheduler.async(() -> {
                        throw new IllegalStateException("boom");
                    }).get(5, TimeUnit.SECONDS));
            assertEquals("boom", thrown.getCause().getMessage());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void rejectsNonPositivePoolSize() {
        assertEquals(ErrorCode.INVALID_ARGUMENT,
                assertThrows(MMOException.class, () -> ExecutorScheduler.direct(0)).code());
    }
}
