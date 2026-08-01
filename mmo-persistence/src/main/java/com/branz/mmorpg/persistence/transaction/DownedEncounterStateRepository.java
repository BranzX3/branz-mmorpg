package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import java.util.List;
import java.util.Optional;

public interface DownedEncounterStateRepository {
    Result<Optional<DownedEncounterStateRecord>, TransactionErrorCode> find(
            EncounterId encounterId);

    Result<List<DownedEncounterStateRecord>, TransactionErrorCode> findRecoverable();

    Result<DownedEncounterStateCommitExecution, TransactionErrorCode> commit(
            TransactionRequest request, DownedEncounterStateCommit commit);
}
