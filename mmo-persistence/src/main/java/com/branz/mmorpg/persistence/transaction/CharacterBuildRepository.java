package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.result.Result;
import java.util.Optional;

public interface CharacterBuildRepository {
    Result<Optional<CharacterBuildRecord>, TransactionErrorCode> find(CharacterId characterId);

    Result<CharacterBuildCommitExecution, TransactionErrorCode> commit(
            TransactionRequest request, CharacterBuildCommit commit);
}
