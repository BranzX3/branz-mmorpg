package com.branz.mmorpg.quest.api;

import java.util.Map;

public record ObjectiveProgress(long current, long target, Map<String, String> data) {
    public ObjectiveProgress {
        data = Map.copyOf(data);
        if (target < 1 || current < 0 || current > target) {
            throw new IllegalArgumentException("invalid objective progress");
        }
    }
    public boolean complete() { return current >= target; }
}
