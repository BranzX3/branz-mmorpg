package com.branz.mmorpg.api.runtime;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Platform-independent scheduling seam.
 *
 * <p>Core code expresses "off the tick thread" and "back on the owning thread"
 * without importing Paper. The Paper module supplies the real implementation;
 * tests supply a direct, single-threaded one.
 *
 * <p>SQL and file I/O run only through {@link #async}. Anything touching a
 * Bukkit object runs only through {@link #sync}, and must re-validate the
 * player's session token first — the player may have logged out while the async
 * half was in flight.
 */
public interface Scheduler {

    /** Runs work off the main thread. */
    <T> CompletableFuture<T> async(Supplier<T> work);

    default CompletableFuture<Void> async(Runnable work) {
        return async(() -> {
            work.run();
            return null;
        });
    }

    /** Runs work on the thread that owns platform state. */
    CompletableFuture<Void> sync(Runnable work);

    /** Runs work on the owning thread after {@code delay}. */
    CompletableFuture<Void> syncLater(Runnable work, Duration delay);

    /**
     * Refuses new work, then drains what is already queued, up to
     * {@code timeout}. Returns false if work remained.
     */
    boolean drainAndShutdown(Duration timeout);
}
