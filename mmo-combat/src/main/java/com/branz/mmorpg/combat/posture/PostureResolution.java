package com.branz.mmorpg.combat.posture;

import java.util.Objects;

public record PostureResolution(PostureRuntime runtime, PosturePhase phase, boolean justBroke) {
    public PostureResolution {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(phase, "phase");
        if (justBroke && phase != PosturePhase.BROKEN) {
            throw new IllegalArgumentException("justBroke requires BROKEN phase");
        }
    }
}
