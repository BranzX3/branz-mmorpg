package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.items.consumable.ConsumableUseState;
import java.util.Objects;
import java.util.UUID;

record DurableConsumableUseState(
        UUID operationId,
        DefinitionId definitionId,
        ConsumableUseState timeline,
        DurableFlaskUsePhase phase,
        long recoveryUntilTick,
        boolean interruptionRequested) {
    DurableConsumableUseState {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(phase, "phase");
        if (!timeline.actionId().equals(operationId)
                || !timeline.consumableId().equals(definitionId)) {
            throw new IllegalArgumentException("Consumable operation and timeline must match");
        }
        if (recoveryUntilTick < -1) {
            throw new IllegalArgumentException("recovery deadline must be absent or non-negative");
        }
        if (phase == DurableFlaskUsePhase.WINDUP && timeline.consumed()) {
            throw new IllegalArgumentException("windup cannot contain a committed use");
        }
        if ((phase == DurableFlaskUsePhase.COMMITTING
                        || phase == DurableFlaskUsePhase.RECOVERY
                        || phase == DurableFlaskUsePhase.COMPLETE
                        || phase == DurableFlaskUsePhase.INTERRUPTED_AFTER_COMMIT)
                && !timeline.consumed()) {
            throw new IllegalArgumentException("post-commit phase requires a committed timeline");
        }
        if (phase == DurableFlaskUsePhase.RECOVERY && recoveryUntilTick < 0) {
            throw new IllegalArgumentException("recovery phase requires a deadline");
        }
        if (phase != DurableFlaskUsePhase.RECOVERY && recoveryUntilTick != -1) {
            throw new IllegalArgumentException("only recovery may carry a deadline");
        }
    }
}
