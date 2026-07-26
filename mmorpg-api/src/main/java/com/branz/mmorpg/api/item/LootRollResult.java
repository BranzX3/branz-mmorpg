package com.branz.mmorpg.api.item;

import java.util.List;

public record LootRollResult(
        String rollId,
        boolean eligible,
        boolean newlyApplied,
        List<LootAward> awards,
        long delivered,
        long overflowed) {
    public LootRollResult {
        awards = List.copyOf(awards);
    }
}
