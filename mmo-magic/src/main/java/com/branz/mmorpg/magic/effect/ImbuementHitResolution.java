package com.branz.mmorpg.magic.effect;

import java.util.Objects;
import java.util.Optional;

public record ImbuementHitResolution(
        boolean applied, Optional<RunicImbuementRuntime> remainingRuntime) {
    public ImbuementHitResolution {
        Objects.requireNonNull(remainingRuntime, "remainingRuntime");
        if (!applied && remainingRuntime.isPresent()) {
            throw new IllegalArgumentException("inactive Imbuement cannot retain a runtime");
        }
    }
}
