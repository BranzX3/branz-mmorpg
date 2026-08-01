package com.branz.mmorpg.persistence.transaction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ResourceNodeStateCommitExecution(
        ResourceNodeStateRecord node,
        Optional<CharacterLifeskillStateRecord> characterState,
        Optional<ItemLocationRecord> tool,
        List<LotLocationRecord> outputLots,
        TransactionExecution transaction) {
    public ResourceNodeStateCommitExecution {
        Objects.requireNonNull(node, "node");
        characterState = Objects.requireNonNull(characterState, "characterState");
        tool = Objects.requireNonNull(tool, "tool");
        outputLots = List.copyOf(Objects.requireNonNull(outputLots, "outputLots"));
        Objects.requireNonNull(transaction, "transaction");
    }
}
