package com.branz.mmorpg.items.consumable;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;
import java.util.UUID;

public record ConsumableUseState(
        UUID actionId,
        DefinitionId consumableId,
        ConsumableUseProfile profile,
        long startedTick,
        long evaluatedTick,
        ConsumableUsePhase phase,
        boolean consumed) {
    public ConsumableUseState {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(consumableId, "consumableId");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(phase, "phase");
        if (startedTick < 0 || evaluatedTick < startedTick) {
            throw new IllegalArgumentException("invalid consumable action ticks");
        }
        if (phase == ConsumableUsePhase.CANCELLED_BEFORE_COMMIT && consumed) {
            throw new IllegalArgumentException("pre-commit cancellation cannot consume the item");
        }
        if (phase != ConsumableUsePhase.WINDUP
                && phase != ConsumableUsePhase.CANCELLED_BEFORE_COMMIT
                && !consumed) {
            throw new IllegalArgumentException("post-commit phases require item consumption");
        }
    }

    public static ConsumableUseState start(
            UUID actionId,
            DefinitionId consumableId,
            ConsumableUseProfile profile,
            long currentTick) {
        return new ConsumableUseState(
                actionId,
                consumableId,
                profile,
                currentTick,
                currentTick,
                ConsumableUsePhase.WINDUP,
                false);
    }
}
