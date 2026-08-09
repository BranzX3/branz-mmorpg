package com.branz.mmorpg.persistence.transaction;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.result.Result;
import java.util.Optional;

public interface CharacterOnboardingStateRepository {
    Result<Optional<CharacterOnboardingStateRecord>, TransactionErrorCode> find(
            CharacterId characterId);

    Result<CharacterOnboardingStateCommitExecution, TransactionErrorCode> chooseFoundation(
            TransactionRequest request, CharacterId characterId, String foundationId);

    Result<CharacterOnboardingStateCommitExecution, TransactionErrorCode> markKitReady(
            TransactionRequest request, CharacterId characterId, long expectedVersion);
}
