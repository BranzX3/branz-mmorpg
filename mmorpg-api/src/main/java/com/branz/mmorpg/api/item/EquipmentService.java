package com.branz.mmorpg.api.item;

import com.branz.mmorpg.api.operation.OperationId;
import java.util.UUID;

public interface EquipmentService {
    InventoryMutationCommit equip(UUID playerId, UUID itemInstanceId,
                                      EquipmentSlot slot, OperationId operationId);

    InventoryMutationCommit unequip(UUID playerId, EquipmentSlot slot, OperationId operationId);
}
