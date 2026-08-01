package com.branz.mmorpg.magic.effect;

import java.util.Objects;

public record ZoneTickResolution(ZoneRuntime runtime, boolean pulseEmitted) {
    public ZoneTickResolution {
        Objects.requireNonNull(runtime, "runtime");
        if (runtime.expired() && pulseEmitted) {
            throw new IllegalArgumentException("an expired zone cannot emit a pulse");
        }
    }
}
