package com.branz.mmorpg.combat.engagement;

import com.branz.mmorpg.combat.state.EngagementState;
import java.util.Objects;

/** Immutable server-tick state for one combatant's engagement lifecycle. */
public record EngagementRuntime(
        EngagementState state, long lastHostileTick, long transitionTick, long revision) {
    public static final long NO_HOSTILE_TICK = -1;

    public EngagementRuntime {
        Objects.requireNonNull(state, "state");
        if (lastHostileTick < NO_HOSTILE_TICK) {
            throw new IllegalArgumentException("lastHostileTick must be -1 or a server tick");
        }
        if (transitionTick < 0) {
            throw new IllegalArgumentException("transitionTick must not be negative");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
    }

    public static EngagementRuntime initial(long currentTick) {
        if (currentTick < 0) {
            throw new IllegalArgumentException("currentTick must not be negative");
        }
        return new EngagementRuntime(EngagementState.EXPLORATION, NO_HOSTILE_TICK, currentTick, 0);
    }
}
