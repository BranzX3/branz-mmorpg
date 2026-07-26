package com.branz.mmorpg.api.mastery;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.operation.OperationId;
import java.util.Map;
import java.util.UUID;

public interface CombatMasteryService {

    Map<ContentId, MasterySnapshot> profile(UUID playerId);

    MasteryMutationCommit grantContribution(UUID playerId, ContentId masteryId,
                                            long baseXp, double antiFarmMultiplier,
                                            OperationId operationId);
}
