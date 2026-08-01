package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.result.Result;
import java.util.Optional;

public interface CharacterExpeditionStateRepository {
    Result<Optional<CharacterExpeditionStateRecord>, TransactionErrorCode> find(
            CharacterId characterId);

    Result<CharacterExpeditionStateCommitExecution, TransactionErrorCode> commit(
            TransactionRequest request, CharacterExpeditionStateCommit commit);

    Result<CharacterFlaskPreparationCommitExecution, TransactionErrorCode> commitFlaskPreparation(
            TransactionRequest request, CharacterFlaskPreparationCommit commit);

    Result<CharacterConsumableUseCommitExecution, TransactionErrorCode> commitConsumableUse(
            TransactionRequest request, CharacterConsumableUseCommit commit);
}
