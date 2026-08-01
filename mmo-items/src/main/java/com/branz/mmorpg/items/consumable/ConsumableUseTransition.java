package com.branz.mmorpg.items.consumable;

import java.util.Objects;

public record ConsumableUseTransition(ConsumableUseState state, boolean commitNow) {
    public ConsumableUseTransition {
        Objects.requireNonNull(state, "state");
        if (commitNow && !state.consumed()) {
            throw new IllegalArgumentException("commit transition must consume the item");
        }
    }
}
