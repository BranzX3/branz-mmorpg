package com.branz.mmorpg.quest.api;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface QuestScheduler {
    <T> CompletableFuture<T> async(Supplier<T> work);
    CompletableFuture<Void> sync(Runnable work);
    CompletableFuture<Void> later(Runnable work, Duration delay);
}
