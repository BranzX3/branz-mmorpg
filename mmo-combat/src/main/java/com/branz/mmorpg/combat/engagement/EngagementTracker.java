package com.branz.mmorpg.combat.engagement;

import com.branz.mmorpg.combat.state.EngagementState;
import java.util.Objects;

/** Deterministic engagement transitions driven only by server ticks and authoritative context. */
public final class EngagementTracker {
    private final int exitTicks;

    public EngagementTracker(int exitTicks) {
        if (exitTicks < 1) {
            throw new IllegalArgumentException("exitTicks must be positive");
        }
        this.exitTicks = exitTicks;
    }

    public int exitTicks() {
        return exitTicks;
    }

    public EngagementRuntime alert(EngagementRuntime current, long tick) {
        validateTick(current, tick);
        if (current.state() == EngagementState.ENGAGED
                || current.state() == EngagementState.ALERT) {
            return current;
        }
        return transition(current, EngagementState.ALERT, tick);
    }

    public EngagementRuntime hostileActivity(EngagementRuntime current, long tick) {
        validateTick(current, tick);
        return new EngagementRuntime(
                EngagementState.ENGAGED,
                tick,
                current.state() == EngagementState.ENGAGED ? current.transitionTick() : tick,
                current.revision() + 1);
    }

    public EngagementRuntime tick(
            EngagementRuntime current, long tick, EngagementTickContext context) {
        Objects.requireNonNull(context, "context");
        validateTick(current, tick);

        if (context.encounterHardLock()) {
            if (current.state() == EngagementState.ENGAGED) {
                return current;
            }
            long lastHostile =
                    current.lastHostileTick() == EngagementRuntime.NO_HOSTILE_TICK
                            ? tick
                            : current.lastHostileTick();
            return new EngagementRuntime(
                    EngagementState.ENGAGED, lastHostile, tick, current.revision() + 1);
        }
        if (context.hostileOwnsThreat()) {
            if (current.state() == EngagementState.EXPLORATION) {
                return transition(current, EngagementState.ALERT, tick);
            }
            if (current.state() == EngagementState.DISENGAGING) {
                return transition(current, EngagementState.ENGAGED, tick);
            }
            return current;
        }
        if (current.state() == EngagementState.ALERT) {
            return transition(current, EngagementState.EXPLORATION, tick);
        }
        if (current.state() == EngagementState.EXPLORATION) {
            return current;
        }
        if (context.downed()) {
            return current.state() == EngagementState.ENGAGED
                    ? current
                    : transition(current, EngagementState.ENGAGED, tick);
        }

        long elapsed = tick - current.lastHostileTick();
        if (elapsed >= exitTicks) {
            return transition(current, EngagementState.EXPLORATION, tick);
        }
        return current.state() == EngagementState.ENGAGED
                ? transition(current, EngagementState.DISENGAGING, tick)
                : current;
    }

    public int remainingExitTicks(EngagementRuntime current, long tick) {
        validateTick(current, tick);
        if (current.lastHostileTick() == EngagementRuntime.NO_HOSTILE_TICK
                || current.state() == EngagementState.EXPLORATION
                || current.state() == EngagementState.ALERT) {
            return 0;
        }
        return (int) Math.max(0, exitTicks - (tick - current.lastHostileTick()));
    }

    private static EngagementRuntime transition(
            EngagementRuntime current, EngagementState next, long tick) {
        if (current.state() == next) {
            return current;
        }
        return new EngagementRuntime(next, current.lastHostileTick(), tick, current.revision() + 1);
    }

    private static void validateTick(EngagementRuntime current, long tick) {
        Objects.requireNonNull(current, "current");
        if (tick < 0
                || tick < current.transitionTick()
                || (current.lastHostileTick() != EngagementRuntime.NO_HOSTILE_TICK
                        && tick < current.lastHostileTick())) {
            throw new IllegalArgumentException("tick must be monotonic");
        }
    }
}
