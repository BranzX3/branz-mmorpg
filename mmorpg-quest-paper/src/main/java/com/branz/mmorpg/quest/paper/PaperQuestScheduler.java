package com.branz.mmorpg.quest.paper;

import com.branz.mmorpg.api.runtime.Scheduler;
import com.branz.mmorpg.quest.api.QuestScheduler;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class PaperQuestScheduler implements QuestScheduler {
    private final Scheduler scheduler;
    public PaperQuestScheduler(Scheduler scheduler) { this.scheduler = scheduler; }
    @Override public <T> CompletableFuture<T> async(Supplier<T> work) {
        return scheduler.async(work);
    }
    @Override public CompletableFuture<Void> sync(Runnable work) {
        return scheduler.sync(work);
    }
    @Override public CompletableFuture<Void> later(Runnable work, Duration delay) {
        return scheduler.syncLater(work, delay);
    }
}
