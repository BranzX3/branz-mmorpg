package com.branz.mmorpg.persistence.progression;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.result.Result;
import java.util.List;
import java.util.Optional;

public interface KnowledgeProgressionRepository {
    Result<List<KnowledgeRecord>, KnowledgePersistenceErrorCode> findKnowledge(
            CharacterId characterId);

    Result<Optional<RenownRecord>, KnowledgePersistenceErrorCode> findRenown(
            CharacterId characterId);

    Result<TeachingCommitExecution, KnowledgePersistenceErrorCode> commitTeaching(
            TeachingCommitRequest request);
}
