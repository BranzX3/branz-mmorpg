package com.branz.mmorpg.lifeskills.progression;

import java.util.Objects;

public record LifeFocusDecision(
        LifeFocusRuntime runtime,
        int recoveredFocus,
        int spentFocus,
        boolean focusedWork,
        boolean replayed) {
    public LifeFocusDecision {
        Objects.requireNonNull(runtime, "runtime");
        if (recoveredFocus < 0 || spentFocus < 0) {
            throw new IllegalArgumentException("Focus changes must be non-negative");
        }
        if (focusedWork != (spentFocus > 0) && !replayed) {
            throw new IllegalArgumentException("focusedWork must match a positive Focus spend");
        }
    }
}
