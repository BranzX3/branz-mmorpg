package com.branz.mmorpg.api.crafting;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.operation.OperationId;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Blocking craft escrow and persistence port. */
public interface CraftingRepository {
    long allocateSequence(UUID playerId, ContentId recipeId);

    ProfessionSnapshot profession(UUID playerId, ContentId professionId);

    Optional<CraftJob> job(OperationId operationId);

    Optional<CraftJob> activeJob(UUID playerId);

    CraftPrepareCommit prepare(
            UUID playerId, RecipeDefinition recipe, long contentRevision,
            Map<ContentId, Long> escrow, OperationId operationId,
            Instant now, UnaryOperator<InventorySnapshot> consumeInputs);

    CraftJob markPaymentSettled(OperationId operationId, Instant readyAt, Instant now);

    CraftJob cancel(
            OperationId operationId, String reason, Instant now,
            UnaryOperator<InventorySnapshot> refundInputs);

    CraftFinalizeCommit finalizeCraft(
            OperationId operationId, Optional<ContentId> professionId, Instant now,
            UnaryOperator<InventorySnapshot> deliverOutput,
            UnaryOperator<ProfessionSnapshot> professionMutation);
}
