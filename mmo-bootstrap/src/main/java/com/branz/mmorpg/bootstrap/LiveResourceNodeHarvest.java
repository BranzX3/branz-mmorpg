package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.persistence.transaction.ResourceNodeStateCommitExecution;
import java.util.Objects;

record LiveResourceNodeHarvest(
        ResourceNodeStateCommitExecution execution,
        ResourceNodeLifeskillState lifeskillState,
        int durabilityRemaining,
        int outputQuantity) {
    LiveResourceNodeHarvest {
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(lifeskillState, "lifeskillState");
        if (durabilityRemaining < 0 || outputQuantity < 1) {
            throw new IllegalArgumentException("invalid live harvest result");
        }
    }
}
