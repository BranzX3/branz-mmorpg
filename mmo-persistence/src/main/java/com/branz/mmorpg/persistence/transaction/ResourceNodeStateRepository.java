package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.lifeskills.node.ResourceNodeId;
import java.util.List;
import java.util.Optional;

public interface ResourceNodeStateRepository {
    Result<Optional<ResourceNodeStateRecord>, TransactionErrorCode> find(ResourceNodeId nodeId);

    Result<List<ResourceNodeStateRecord>, TransactionErrorCode> findRecoverable();

    Result<Optional<CharacterLifeskillStateRecord>, TransactionErrorCode> findCharacterState(
            CharacterId characterId);

    Result<ResourceNodeStateCommitExecution, TransactionErrorCode> commit(
            TransactionRequest request, ResourceNodeStateCommit commit);
}
