package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.items.consumable.ConsumableUseEngine;
import com.branz.mmorpg.items.consumable.ConsumableUsePhase;
import com.branz.mmorpg.items.consumable.ConsumableUseProfile;
import com.branz.mmorpg.items.consumable.ConsumableUseState;
import com.branz.mmorpg.items.consumable.ConsumableUseTransition;
import java.util.Objects;
import java.util.UUID;

/** Adds the asynchronous atomic lot/effect commit gate to an authored consumable timeline. */
final class DurableConsumableUseEngine {
    private final ConsumableUseEngine timelines = new ConsumableUseEngine();

    DurableConsumableUseState start(
            UUID operationId,
            DefinitionId definitionId,
            ConsumableUseProfile profile,
            long currentTick) {
        return new DurableConsumableUseState(
                operationId,
                definitionId,
                ConsumableUseState.start(operationId, definitionId, profile, currentTick),
                DurableFlaskUsePhase.WINDUP,
                -1,
                false);
    }

    DurableConsumableUseTransition tick(DurableConsumableUseState state, long currentTick) {
        Objects.requireNonNull(state, "state");
        if (state.phase() == DurableFlaskUsePhase.RECOVERY) {
            return currentTick >= state.recoveryUntilTick()
                    ? new DurableConsumableUseTransition(
                            copy(state, DurableFlaskUsePhase.COMPLETE, -1, false), false)
                    : new DurableConsumableUseTransition(state, false);
        }
        if (state.phase() != DurableFlaskUsePhase.WINDUP) {
            return new DurableConsumableUseTransition(state, false);
        }
        ConsumableUseTransition advanced = timelines.advance(state.timeline(), currentTick, false);
        return advanced.commitNow()
                ? new DurableConsumableUseTransition(
                        withTimeline(
                                state, advanced.state(), DurableFlaskUsePhase.COMMITTING, false),
                        true)
                : new DurableConsumableUseTransition(
                        withTimeline(state, advanced.state(), DurableFlaskUsePhase.WINDUP, false),
                        false);
    }

    DurableConsumableUseTransition interrupt(DurableConsumableUseState state, long currentTick) {
        Objects.requireNonNull(state, "state");
        if (state.phase() == DurableFlaskUsePhase.WINDUP) {
            ConsumableUseTransition interrupted =
                    timelines.advance(state.timeline(), currentTick, true);
            if (interrupted.commitNow()) {
                return new DurableConsumableUseTransition(
                        withTimeline(
                                state, interrupted.state(), DurableFlaskUsePhase.COMMITTING, true),
                        true);
            }
            if (interrupted.state().phase() != ConsumableUsePhase.CANCELLED_BEFORE_COMMIT) {
                throw new IllegalStateException("pre-commit interruption must cancel the timeline");
            }
            return new DurableConsumableUseTransition(
                    withTimeline(
                            state,
                            interrupted.state(),
                            DurableFlaskUsePhase.CANCELLED_BEFORE_COMMIT,
                            false),
                    false);
        }
        if (state.phase() == DurableFlaskUsePhase.COMMITTING) {
            return new DurableConsumableUseTransition(
                    copy(state, DurableFlaskUsePhase.COMMITTING, -1, true), false);
        }
        if (state.phase() == DurableFlaskUsePhase.RECOVERY) {
            return new DurableConsumableUseTransition(
                    copy(state, DurableFlaskUsePhase.INTERRUPTED_AFTER_COMMIT, -1, true), false);
        }
        return new DurableConsumableUseTransition(state, false);
    }

    DurableConsumableUseState commitSucceeded(DurableConsumableUseState state, long currentTick) {
        requireCommitting(state);
        return state.interruptionRequested()
                ? copy(state, DurableFlaskUsePhase.INTERRUPTED_AFTER_COMMIT, -1, true)
                : copy(
                        state,
                        DurableFlaskUsePhase.RECOVERY,
                        Math.addExact(currentTick, state.timeline().profile().recoveryTicks()),
                        false);
    }

    DurableConsumableUseState commitFailed(DurableConsumableUseState state) {
        requireCommitting(state);
        return copy(state, DurableFlaskUsePhase.COMMIT_FAILED, -1, state.interruptionRequested());
    }

    private static void requireCommitting(DurableConsumableUseState state) {
        Objects.requireNonNull(state, "state");
        if (state.phase() != DurableFlaskUsePhase.COMMITTING) {
            throw new IllegalStateException("Consumable durable result requires COMMITTING phase");
        }
    }

    private static DurableConsumableUseState withTimeline(
            DurableConsumableUseState state,
            ConsumableUseState timeline,
            DurableFlaskUsePhase phase,
            boolean interruptionRequested) {
        return new DurableConsumableUseState(
                state.operationId(),
                state.definitionId(),
                timeline,
                phase,
                -1,
                interruptionRequested);
    }

    private static DurableConsumableUseState copy(
            DurableConsumableUseState state,
            DurableFlaskUsePhase phase,
            long recoveryUntilTick,
            boolean interruptionRequested) {
        return new DurableConsumableUseState(
                state.operationId(),
                state.definitionId(),
                state.timeline(),
                phase,
                recoveryUntilTick,
                interruptionRequested);
    }
}
