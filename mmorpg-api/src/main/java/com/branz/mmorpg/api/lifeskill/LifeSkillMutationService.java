package com.branz.mmorpg.api.lifeskill;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.operation.OperationId;
import java.util.UUID;

/** Idempotent public mutation surface consumed by gathering, Quest, and admin tools. */
public interface LifeSkillMutationService {

    LifeSkillMutationCommit grantXp(UUID playerId, ContentId skillId, long amount,
                                    OperationId operationId);

    LifeSkillMutationCommit purchase(UUID playerId, ContentId skillId, ContentId nodeId,
                                     OperationId operationId);

    LifeSkillMutationCommit respec(UUID playerId, ContentId skillId, OperationId operationId);
}
