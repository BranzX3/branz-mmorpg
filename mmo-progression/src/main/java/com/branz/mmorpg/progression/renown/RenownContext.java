package com.branz.mmorpg.progression.renown;

public record RenownContext(long currentRenown, int identicalDeedsToday, boolean duplicateDeedId) {
    public RenownContext {
        if (currentRenown < 0 || identicalDeedsToday < 0) {
            throw new IllegalArgumentException("renown context values must not be negative");
        }
    }
}
