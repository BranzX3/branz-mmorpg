package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.lifeskills.node.ResourceNodeId;
import java.time.Instant;
import java.util.Objects;

public record ResourceNodeStateRecord(
        ResourceNodeId nodeId,
        DefinitionId definitionId,
        String phase,
        String statePayloadJson,
        String contentVersion,
        long version,
        TransactionId lastTransactionId,
        Instant createdAt,
        Instant updatedAt) {
    public ResourceNodeStateRecord {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(definitionId, "definitionId");
        phase = requireText(phase, "phase");
        statePayloadJson = requireText(statePayloadJson, "statePayloadJson");
        contentVersion = requireText(contentVersion, "contentVersion");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(lastTransactionId, "lastTransactionId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
