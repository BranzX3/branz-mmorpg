package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.lifeskills.node.ResourceNodeId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One atomic node reservation/cancel/harvest/recovery value boundary. */
public record ResourceNodeStateCommit(
        ResourceNodeCommitKind kind,
        ResourceNodeId nodeId,
        DefinitionId definitionId,
        String phase,
        long expectedNodeVersion,
        String replacementNodePayloadJson,
        Optional<CharacterId> actor,
        Optional<CharacterLifeskillStateMutation> characterState,
        Optional<ItemPayloadUpdate> toolUpdate,
        List<NewLotLocation> outputLots) {
    public ResourceNodeStateCommit {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(definitionId, "definitionId");
        phase = requireText(phase, "phase");
        if (expectedNodeVersion < 0) {
            throw new IllegalArgumentException("expectedNodeVersion must not be negative");
        }
        replacementNodePayloadJson =
                requireText(replacementNodePayloadJson, "replacementNodePayloadJson");
        actor = Objects.requireNonNull(actor, "actor");
        characterState = Objects.requireNonNull(characterState, "characterState");
        toolUpdate = Objects.requireNonNull(toolUpdate, "toolUpdate");
        outputLots = List.copyOf(Objects.requireNonNull(outputLots, "outputLots"));
        if (outputLots.stream().map(NewLotLocation::lotId).distinct().count()
                != outputLots.size()) {
            throw new IllegalArgumentException("resource-node output lot IDs must be unique");
        }
        switch (kind) {
            case RESERVE, CANCEL -> {
                requireActorAndTool(actor, toolUpdate);
                if (characterState.isPresent() || !outputLots.isEmpty()) {
                    throw new IllegalArgumentException(
                            "reservation/cancel cannot mutate progression or create output lots");
                }
            }
            case HARVEST -> {
                requireActorAndTool(actor, toolUpdate);
                CharacterId harvestActor = actor.orElseThrow();
                if (characterState.isEmpty() || outputLots.isEmpty()) {
                    throw new IllegalArgumentException(
                            "harvest must mutate Lifeskill state and create output lots");
                }
                if (!characterState.orElseThrow().characterId().equals(harvestActor)) {
                    throw new IllegalArgumentException(
                            "harvest Lifeskill state must belong to the actor");
                }
                if (outputLots.stream()
                        .anyMatch(
                                lot ->
                                        lot.ownerCharacterId()
                                                        .filter(harvestActor::equals)
                                                        .isEmpty()
                                                || lot.location().type()
                                                        != ValueLocationType.PENDING_REWARDS)) {
                    throw new IllegalArgumentException(
                            "harvest outputs must enter the actor's Pending Rewards");
                }
            }
            case RECOVER -> {
                if (actor.isPresent() || characterState.isPresent() || !outputLots.isEmpty()) {
                    throw new IllegalArgumentException(
                            "system recovery cannot mutate progression or create output lots");
                }
            }
            default -> throw new IllegalStateException("unsupported resource-node commit kind");
        }
    }

    private static void requireActorAndTool(
            Optional<CharacterId> actor, Optional<ItemPayloadUpdate> toolUpdate) {
        if (actor.isEmpty() || toolUpdate.isEmpty()) {
            throw new IllegalArgumentException(
                    "node value operation requires actor and exact tool");
        }
        if (toolUpdate
                .orElseThrow()
                .expectedOwnerCharacterId()
                .filter(actor.orElseThrow()::equals)
                .isEmpty()) {
            throw new IllegalArgumentException("node tool must belong to the actor");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
