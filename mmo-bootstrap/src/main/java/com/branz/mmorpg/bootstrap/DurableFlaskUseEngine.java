package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.combat.resource.FlaskDose;
import com.branz.mmorpg.items.consumable.ConsumableUseEngine;
import com.branz.mmorpg.items.consumable.ConsumableUsePhase;
import com.branz.mmorpg.items.consumable.ConsumableUseProfile;
import com.branz.mmorpg.items.consumable.ConsumableUseState;
import com.branz.mmorpg.items.consumable.ConsumableUseTransition;
import java.util.Objects;
import java.util.UUID;

/** Adds an asynchronous durable-commit gate to the deterministic consumable timeline. */
final class DurableFlaskUseEngine {
    private static final DefinitionId EXPEDITION_FLASK =
            DefinitionId.of("consumable.expedition_flask");
    private final ConsumableUseEngine timelines = new ConsumableUseEngine();

    DurableFlaskUseState start(UUID operationId, FlaskDose dose, long currentTick) {
        return new DurableFlaskUseState(
                operationId,
                dose,
                ConsumableUseState.start(
                        operationId,
                        EXPEDITION_FLASK,
                        ConsumableUseProfile.expeditionFlask(),
                        currentTick),
                DurableFlaskUsePhase.WINDUP,
                -1,
                false);
    }

    DurableFlaskUseTransition tick(DurableFlaskUseState state, long currentTick) {
        Objects.requireNonNull(state, "state");
        if (state.phase() == DurableFlaskUsePhase.RECOVERY) {
            if (currentTick >= state.recoveryUntilTick()) {
                return new DurableFlaskUseTransition(
                        copy(state, DurableFlaskUsePhase.COMPLETE, -1, false), false);
            }
            return new DurableFlaskUseTransition(state, false);
        }
        if (state.phase() != DurableFlaskUsePhase.WINDUP) {
            return new DurableFlaskUseTransition(state, false);
        }
        ConsumableUseTransition advanced = timelines.advance(state.timeline(), currentTick, false);
        if (!advanced.commitNow()) {
            return new DurableFlaskUseTransition(
                    withTimeline(state, advanced.state(), DurableFlaskUsePhase.WINDUP, false),
                    false);
        }
        return new DurableFlaskUseTransition(
                withTimeline(state, advanced.state(), DurableFlaskUsePhase.COMMITTING, false),
                true);
    }

    DurableFlaskUseTransition interrupt(DurableFlaskUseState state, long currentTick) {
        Objects.requireNonNull(state, "state");
        if (state.phase() == DurableFlaskUsePhase.WINDUP) {
            ConsumableUseTransition interrupted =
                    timelines.advance(state.timeline(), currentTick, true);
            if (interrupted.commitNow()) {
                return new DurableFlaskUseTransition(
                        withTimeline(
                                state, interrupted.state(), DurableFlaskUsePhase.COMMITTING, true),
                        true);
            }
            if (interrupted.state().phase() != ConsumableUsePhase.CANCELLED_BEFORE_COMMIT) {
                throw new IllegalStateException("pre-commit interruption must cancel the timeline");
            }
            return new DurableFlaskUseTransition(
                    withTimeline(
                            state,
                            interrupted.state(),
                            DurableFlaskUsePhase.CANCELLED_BEFORE_COMMIT,
                            false),
                    false);
        }
        if (state.phase() == DurableFlaskUsePhase.COMMITTING) {
            return new DurableFlaskUseTransition(
                    copy(state, DurableFlaskUsePhase.COMMITTING, -1, true), false);
        }
        if (state.phase() == DurableFlaskUsePhase.RECOVERY) {
            return new DurableFlaskUseTransition(
                    copy(state, DurableFlaskUsePhase.INTERRUPTED_AFTER_COMMIT, -1, true), false);
        }
        return new DurableFlaskUseTransition(state, false);
    }

    DurableFlaskUseState commitSucceeded(DurableFlaskUseState state, long currentTick) {
        requireCommitting(state);
        if (state.interruptionRequested()) {
            return copy(state, DurableFlaskUsePhase.INTERRUPTED_AFTER_COMMIT, -1, true);
        }
        return copy(
                state,
                DurableFlaskUsePhase.RECOVERY,
                Math.addExact(currentTick, state.timeline().profile().recoveryTicks()),
                false);
    }

    DurableFlaskUseState commitFailed(DurableFlaskUseState state) {
        requireCommitting(state);
        return copy(state, DurableFlaskUsePhase.COMMIT_FAILED, -1, state.interruptionRequested());
    }

    private static void requireCommitting(DurableFlaskUseState state) {
        Objects.requireNonNull(state, "state");
        if (state.phase() != DurableFlaskUsePhase.COMMITTING) {
            throw new IllegalStateException("Flask durable result requires COMMITTING phase");
        }
    }

    private static DurableFlaskUseState withTimeline(
            DurableFlaskUseState state,
            ConsumableUseState timeline,
            DurableFlaskUsePhase phase,
            boolean interruptionRequested) {
        return new DurableFlaskUseState(
                state.operationId(), state.dose(), timeline, phase, -1, interruptionRequested);
    }

    private static DurableFlaskUseState copy(
            DurableFlaskUseState state,
            DurableFlaskUsePhase phase,
            long recoveryUntilTick,
            boolean interruptionRequested) {
        return new DurableFlaskUseState(
                state.operationId(),
                state.dose(),
                state.timeline(),
                phase,
                recoveryUntilTick,
                interruptionRequested);
    }
}
