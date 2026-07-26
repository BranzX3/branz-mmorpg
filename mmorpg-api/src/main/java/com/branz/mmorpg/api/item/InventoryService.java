package com.branz.mmorpg.api.item;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.operation.OperationId;
import java.util.UUID;

public interface InventoryService {
    InventorySnapshot inventory(UUID playerId);

    InventoryMutationCommit grantMaterial(
            UUID playerId, ContentId materialId, long quantity, OperationId operationId);

    InventoryMutationCommit grantUnique(
            UUID playerId, ItemInstance item, OperationId operationId);

    InventoryMutationCommit claimMaterial(
            UUID playerId, ContentId materialId, long quantity, OperationId operationId);

    InventoryMutationCommit claimUnique(
            UUID playerId, UUID itemInstanceId, OperationId operationId);

    InventoryMutationCommit revokeMaterial(
            UUID playerId, ContentId materialId, long quantity, OperationId operationId);

    InventoryMutationCommit revokeUnique(
            UUID playerId, UUID itemInstanceId, OperationId operationId);
}
