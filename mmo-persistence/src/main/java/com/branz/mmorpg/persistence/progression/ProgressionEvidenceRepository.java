package com.branz.mmorpg.persistence.progression;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.progression.evidence.EvidenceCandidate;
import java.util.List;

public interface ProgressionEvidenceRepository {
    Result<List<ProgressionTrackRecord>, ProgressionPersistenceErrorCode> findTracks(
            CharacterId characterId);

    Result<List<ProgressionEvidenceExecution>, ProgressionPersistenceErrorCode> recordBatch(
            List<EvidenceCandidate> candidates);
}
