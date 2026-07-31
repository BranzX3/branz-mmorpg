package com.branz.mmorpg.combat.hitbox;

import java.util.List;
import java.util.Objects;

public record SweptArcResolution(
        List<ResolvedTarget> targets, List<CombatVector> sampledOrigins, boolean samplingCapped) {
    public SweptArcResolution {
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        sampledOrigins = List.copyOf(Objects.requireNonNull(sampledOrigins, "sampledOrigins"));
        if (sampledOrigins.size() < 2) {
            throw new IllegalArgumentException("swept ARC requires both endpoint samples");
        }
    }
}
