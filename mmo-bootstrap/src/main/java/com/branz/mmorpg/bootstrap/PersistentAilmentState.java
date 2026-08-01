package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.combat.status.AilmentType;
import java.util.Objects;

record PersistentAilmentState(
        AilmentType type,
        double buildup,
        int decayDelayRemainingTicks,
        int activeRemainingTicks,
        int tier) {
    PersistentAilmentState {
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(buildup)
                || buildup < 0
                || decayDelayRemainingTicks < 0
                || activeRemainingTicks < 0
                || tier < 0
                || (activeRemainingTicks == 0) != (tier == 0)) {
            throw new IllegalArgumentException("invalid persistent ailment state");
        }
    }
}
