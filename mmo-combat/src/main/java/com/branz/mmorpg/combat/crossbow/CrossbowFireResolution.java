package com.branz.mmorpg.combat.crossbow;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;

/** Fire intent after which the loaded checkpoint must be cleared durably before launch. */
public record CrossbowFireResolution(CrossbowRuntime runtime, DefinitionId boundAmmoDefinitionId) {
    public CrossbowFireResolution {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(boundAmmoDefinitionId, "boundAmmoDefinitionId");
        if (runtime.phase() != CrossbowPhase.FIRED) {
            throw new IllegalArgumentException("A fired Crossbow must enter the FIRED phase");
        }
    }
}
