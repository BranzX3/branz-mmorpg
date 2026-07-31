package com.branz.mmorpg.combat.crossbow;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;
import java.util.Optional;

/** Minimal item-owned Crossbow state reconstructed after swap, reconnect or restart. */
public record CrossbowPersistentState(
        CrossbowCheckpoint checkpoint, Optional<DefinitionId> boundAmmo) {
    public CrossbowPersistentState {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(boundAmmo, "boundAmmo");
        if ((checkpoint == CrossbowCheckpoint.UNLOADED) == boundAmmo.isPresent()) {
            throw new IllegalArgumentException(
                    "Only BOLT_PLACED and LOADED checkpoints require bound ammo");
        }
    }

    public static CrossbowPersistentState unloaded() {
        return new CrossbowPersistentState(CrossbowCheckpoint.UNLOADED, Optional.empty());
    }

    public static CrossbowPersistentState boltPlaced(DefinitionId ammoDefinitionId) {
        return new CrossbowPersistentState(
                CrossbowCheckpoint.BOLT_PLACED,
                Optional.of(Objects.requireNonNull(ammoDefinitionId, "ammoDefinitionId")));
    }

    public static CrossbowPersistentState loaded(DefinitionId ammoDefinitionId) {
        return new CrossbowPersistentState(
                CrossbowCheckpoint.LOADED,
                Optional.of(Objects.requireNonNull(ammoDefinitionId, "ammoDefinitionId")));
    }
}
