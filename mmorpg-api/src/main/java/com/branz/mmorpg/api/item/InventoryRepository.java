package com.branz.mmorpg.api.item;

import com.branz.mmorpg.api.operation.OperationId;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Blocking authoritative inventory persistence port. */
public interface InventoryRepository {
    InventorySnapshot load(UUID playerId);

    InventoryMutationCommit mutate(UUID playerId, OperationId operationId,
                                   long delivered, long overflowed,
                                   UnaryOperator<InventorySnapshot> mutation);
}
