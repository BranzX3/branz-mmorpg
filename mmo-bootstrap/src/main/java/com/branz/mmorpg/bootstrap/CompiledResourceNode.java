package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.lifeskills.node.ResourceNodeDefinition;
import com.branz.mmorpg.lifeskills.progression.LifeskillRankTable;
import java.util.Objects;

record CompiledResourceNode(
        ResourceNodeDefinition definition,
        DefinitionId toolDefinitionId,
        DefinitionId outputDefinitionId,
        int outputQuantity,
        double rankEvidence,
        LifeskillRankTable rankTable) {
    CompiledResourceNode {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(toolDefinitionId, "toolDefinitionId");
        Objects.requireNonNull(outputDefinitionId, "outputDefinitionId");
        Objects.requireNonNull(rankTable, "rankTable");
        if (outputQuantity < 1 || rankEvidence <= 0 || !Double.isFinite(rankEvidence)) {
            throw new IllegalArgumentException("node output and rank evidence must be positive");
        }
    }
}
