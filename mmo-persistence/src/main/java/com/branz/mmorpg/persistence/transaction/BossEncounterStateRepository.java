package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import java.util.List;
import java.util.Optional;

public interface BossEncounterStateRepository {
    Result<Optional<BossEncounterStateRecord>, TransactionErrorCode> find(EncounterId encounterId);

    Result<List<BossEncounterStateRecord>, TransactionErrorCode> findRecoverable();

    Result<BossEncounterStateCommitExecution, TransactionErrorCode> commit(
            TransactionRequest request, BossEncounterStateCommit commit);
}
