package com.branz.mmorpg.core.fixture;

import com.branz.mmorpg.api.runtime.Scheduler;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Scheduler fixture that runs everything inline on the calling thread, so tests
 * of async flows stay deterministic and need no waiting.
 */
public final class DirectScheduler implements Scheduler {

    @Override
    public <T> CompletableFuture<T> async(Supplier<T> work) {
        try {
            return CompletableFuture.completedFuture(work.get());
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public CompletableFuture<Void> sync(Runnable work) {
        work.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> syncLater(Runnable work, Duration delay) {
        work.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean drainAndShutdown(Duration timeout) {
        return true;
    }
}
