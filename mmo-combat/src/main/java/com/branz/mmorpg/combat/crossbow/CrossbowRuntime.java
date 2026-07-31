package com.branz.mmorpg.combat.crossbow;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;
import java.util.Optional;

/** Immutable live Crossbow state. Transient phases collapse to their last durable checkpoint. */
public record CrossbowRuntime(
        CrossbowPhase phase, long phaseStartedTick, Optional<DefinitionId> boundAmmo) {
    public CrossbowRuntime {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(boundAmmo, "boundAmmo");
        if (phaseStartedTick < 0) {
            throw new IllegalArgumentException("phaseStartedTick must not be negative");
        }
        boolean requiresAmmo =
                phase == CrossbowPhase.BOLT_PLACED
                        || phase == CrossbowPhase.LOCKING
                        || phase == CrossbowPhase.LOADED;
        if (requiresAmmo != boundAmmo.isPresent()) {
            throw new IllegalArgumentException(
                    "Crossbow bound ammo must match its durable checkpoint");
        }
    }

    public static CrossbowRuntime restore(CrossbowPersistentState state, long tick) {
        Objects.requireNonNull(state, "state");
        return switch (state.checkpoint()) {
            case UNLOADED -> new CrossbowRuntime(CrossbowPhase.UNLOADED, tick, Optional.empty());
            case BOLT_PLACED ->
                    new CrossbowRuntime(CrossbowPhase.BOLT_PLACED, tick, state.boundAmmo());
            case LOADED -> new CrossbowRuntime(CrossbowPhase.LOADED, tick, state.boundAmmo());
        };
    }

    public CrossbowPersistentState persistentState() {
        return switch (phase) {
            case UNLOADED, COCKING, FIRED -> CrossbowPersistentState.unloaded();
            case BOLT_PLACED, LOCKING ->
                    CrossbowPersistentState.boltPlaced(boundAmmo.orElseThrow());
            case LOADED -> CrossbowPersistentState.loaded(boundAmmo.orElseThrow());
        };
    }
}
