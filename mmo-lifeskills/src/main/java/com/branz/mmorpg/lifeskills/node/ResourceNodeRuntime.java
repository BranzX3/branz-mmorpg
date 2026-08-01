package com.branz.mmorpg.lifeskills.node;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ResourceNodeRuntime(
        ResourceNodeId nodeId,
        DefinitionId definitionId,
        Map<ResourceNodeAccessKey, ResourceNodeSlot> slots,
        Map<UUID, ResourceNodeOperation> processedOperations) {
    public ResourceNodeRuntime {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(definitionId, "definitionId");
        slots = Map.copyOf(Objects.requireNonNull(slots, "slots"));
        processedOperations =
                Map.copyOf(Objects.requireNonNull(processedOperations, "processedOperations"));
    }

    public static ResourceNodeRuntime initial(
            ResourceNodeId nodeId, ResourceNodeDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return new ResourceNodeRuntime(nodeId, definition.id(), Map.of(), Map.of());
    }
}
