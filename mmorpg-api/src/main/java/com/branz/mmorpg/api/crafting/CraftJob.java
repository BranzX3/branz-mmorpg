package com.branz.mmorpg.api.crafting;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.operation.OperationId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CraftJob(
        OperationId operationId,
        UUID playerId,
        ContentId recipeId,
        long contentRevision,
        Status status,
        Map<ContentId, Long> escrowedMaterials,
        long coinFee,
        long durationMillis,
        ContentId outputItemId,
        long outputQuantity,
        RecipeDefinition.Output.Binding outputBinding,
        String qualityPolicy,
        Optional<ContentId> professionId,
        long professionXp,
        int trivialAfterLevel,
        Optional<Instant> readyAt,
        Optional<String> failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public enum Status { PENDING_PAYMENT, IN_PROGRESS, COMPLETE, CANCELLED }

    public CraftJob {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(status, "status");
        escrowedMaterials = Map.copyOf(escrowedMaterials);
        Objects.requireNonNull(outputItemId, "outputItemId");
        Objects.requireNonNull(outputBinding, "outputBinding");
        qualityPolicy = Objects.requireNonNull(qualityPolicy, "qualityPolicy").trim();
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(readyAt, "readyAt");
        Objects.requireNonNull(failureReason, "failureReason");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!playerId.equals(operationId.playerUuid()) || contentRevision < 0 || coinFee < 0
                || durationMillis < 0
                || outputQuantity < 1 || qualityPolicy.isEmpty() || professionXp < 0
                || trivialAfterLevel < 1) {
            throw new IllegalArgumentException("invalid craft job identity");
        }
        if ((status == Status.IN_PROGRESS || status == Status.COMPLETE) && readyAt.isEmpty()) {
            throw new IllegalArgumentException("started craft lacks readyAt");
        }
    }
}
