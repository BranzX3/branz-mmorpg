package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.result.Result;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeathPouchRepository {
    Result<Optional<DeathPouchRecord>, TransactionErrorCode> find(UUID pouchId);

    Result<List<DeathPouchRecord>, TransactionErrorCode> findActive(CharacterId ownerCharacterId);

    Result<List<DeathPouchRecord>, TransactionErrorCode> findRecoverable();

    Result<List<DeathPouchRecord>, TransactionErrorCode> findExpirable(Instant now);

    Result<DeathPouchCommitExecution, TransactionErrorCode> commit(
            TransactionRequest request, DeathPouchCommit commit);
}
