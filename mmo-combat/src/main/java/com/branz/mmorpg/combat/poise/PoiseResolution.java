package com.branz.mmorpg.combat.poise;

import com.branz.mmorpg.combat.cc.CcSeverity;
import java.util.Objects;
import java.util.Optional;

public record PoiseResolution(
        PoiseRuntime runtime, boolean resisted, Optional<CcSeverity> triggeredSeverity) {
    public PoiseResolution {
        Objects.requireNonNull(runtime, "runtime");
        triggeredSeverity = Objects.requireNonNull(triggeredSeverity, "triggeredSeverity");
        if (resisted == triggeredSeverity.isPresent()) {
            throw new IllegalArgumentException("poise result must resist or trigger exactly once");
        }
    }
}
