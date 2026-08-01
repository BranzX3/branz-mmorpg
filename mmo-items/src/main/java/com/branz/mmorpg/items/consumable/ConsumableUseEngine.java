package com.branz.mmorpg.items.consumable;

import java.util.Objects;

/** Deterministic windup/commit/recovery state machine shared by Flask and item consumables. */
public final class ConsumableUseEngine {
    public ConsumableUseTransition advance(
            ConsumableUseState state, long currentTick, boolean interrupted) {
        Objects.requireNonNull(state, "state");
        if (currentTick < state.evaluatedTick()) {
            throw new IllegalArgumentException("currentTick must be monotonic");
        }
        if (state.phase().terminal()) {
            return new ConsumableUseTransition(state, false);
        }
        long elapsed = currentTick - state.startedTick();
        boolean reachesCommit = elapsed >= state.profile().commitTick();
        boolean commitNow = !state.consumed() && reachesCommit;
        boolean consumed = state.consumed() || reachesCommit;
        ConsumableUsePhase phase;
        if (interrupted) {
            phase =
                    consumed
                            ? ConsumableUsePhase.INTERRUPTED_AFTER_COMMIT
                            : ConsumableUsePhase.CANCELLED_BEFORE_COMMIT;
        } else if (elapsed >= state.profile().completeTickOffset()) {
            phase = ConsumableUsePhase.COMPLETE;
        } else if (elapsed >= state.profile().windupTicks()) {
            phase = ConsumableUsePhase.RECOVERY;
        } else if (consumed) {
            phase = ConsumableUsePhase.COMMITTED;
        } else {
            phase = ConsumableUsePhase.WINDUP;
        }
        return new ConsumableUseTransition(
                new ConsumableUseState(
                        state.actionId(),
                        state.consumableId(),
                        state.profile(),
                        state.startedTick(),
                        currentTick,
                        phase,
                        consumed),
                commitNow);
    }
}
