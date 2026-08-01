package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.result.Result;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonalRewardGrantRepository {
    Result<Optional<PersonalRewardGrantRecord>, TransactionErrorCode> find(UUID grantId);

    Result<List<PersonalRewardGrantRecord>, TransactionErrorCode> findPending();

    Result<PersonalRewardGrantCommitExecution, TransactionErrorCode> commit(
            TransactionRequest request, PersonalRewardGrantCommit commit);
}
