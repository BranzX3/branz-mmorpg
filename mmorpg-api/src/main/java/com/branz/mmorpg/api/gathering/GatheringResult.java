package com.branz.mmorpg.api.gathering;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Map;

public record GatheringResult(
        boolean applied,
        GatheringNodeInstance node,
        long awardedXp,
        Map<ContentId, Long> yields,
        Instant respawnAt) {
    public GatheringResult {
        yields = Map.copyOf(yields);
    }
}
