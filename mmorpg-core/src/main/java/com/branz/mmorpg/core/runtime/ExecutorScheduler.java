package com.branz.mmorpg.core.runtime;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.runtime.Scheduler;
import com.branz.mmorpg.core.service.AbstractService;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * {@link Scheduler} owning a bounded async pool, plus a caller-supplied
 * executor for work that must run on the platform's owning thread.
 *
 * <p>On a Paper server the sync executor is the main-thread scheduler; in tests
 * it is {@link Runnable#run} on the calling thread. Because this is a
 * {@link AbstractService}, the container's stop path is what guarantees the
 * pools die with it — the "no leaked executors" acceptance criterion is a test
 * of {@code stop()}, not of a shutdown hook.
 */
public final class ExecutorScheduler extends AbstractService implements Scheduler {

    private final int poolSize;
    private final Executor syncExecutor;
    private volatile ExecutorService asyncPool;
    private volatile ScheduledExecutorService delayPool;
    private volatile boolean accepting;

    public ExecutorScheduler(int poolSize, Executor syncExecutor) {
        super("scheduler");
        if (poolSize <= 0) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT, "poolSize must be positive: " + poolSize);
        }
        this.poolSize = poolSize;
        this.syncExecutor = Objects.requireNonNull(syncExecutor, "syncExecutor");
    }

    /** Test/console variant: sync work runs inline on the calling thread. */
    public static ExecutorScheduler direct(int poolSize) {
        return new ExecutorScheduler(poolSize, Runnable::run);
    }

    @Override
    protected void onStart() {
        asyncPool = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "branz-mmorpg-async");
            thread.setDaemon(true);
            return thread;
        });
        delayPool = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "branz-mmorpg-delay");
            thread.setDaemon(true);
            return thread;
        });
        accepting = true;
    }

    @Override
    protected void onStop() {
        accepting = false;
        shutdown(delayPool);
        shutdown(asyncPool);
        delayPool = null;
        asyncPool = null;
    }

    @Override
    public <T> CompletableFuture<T> async(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        ExecutorService pool = requireAccepting(asyncPool);
        return CompletableFuture.supplyAsync(work, pool);
    }

    @Override
    public CompletableFuture<Void> sync(Runnable work) {
        Objects.requireNonNull(work, "work");
        requireAccepting(asyncPool);
        return CompletableFuture.runAsync(work, syncExecutor);
    }

    @Override
    public CompletableFuture<Void> syncLater(Runnable work, Duration delay) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(delay, "delay");
        ScheduledExecutorService pool = requireAccepting(delayPool);
        CompletableFuture<Void> result = new CompletableFuture<>();
        pool.schedule(
                () -> syncExecutor.execute(() -> {
                    try {
                        work.run();
                        result.complete(null);
                    } catch (RuntimeException exception) {
                        result.completeExceptionally(exception);
                    }
                }),
                Math.max(0L, delay.toMillis()),
                TimeUnit.MILLISECONDS);
        return result;
    }

    @Override
    public boolean drainAndShutdown(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        accepting = false;
        ExecutorService async = asyncPool;
        ScheduledExecutorService delay = delayPool;
        boolean drained = true;
        if (delay != null) {
            delay.shutdown();
            drained = awaitTermination(delay, timeout);
        }
        if (async != null) {
            async.shutdown();
            drained &= awaitTermination(async, timeout);
        }
        return drained;
    }

    private <T extends ExecutorService> T requireAccepting(T pool) {
        if (!accepting || pool == null) {
            throw new MMOException(ErrorCode.SHUTTING_DOWN, "scheduler is not accepting work");
        }
        return pool;
    }

    private static boolean awaitTermination(ExecutorService pool, Duration timeout) {
        try {
            return pool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void shutdown(ExecutorService pool) {
        if (pool == null) {
            return;
        }
        pool.shutdown();
        if (!awaitTermination(pool, Duration.ofSeconds(5))) {
            pool.shutdownNow();
        }
    }
}
