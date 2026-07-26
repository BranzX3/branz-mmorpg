package com.branz.mmorpg.api.mastery;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.operation.OperationId;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Blocking persistence port; callers run it off the Paper tick thread. */
public interface CombatMasteryRepository {

    Map<ContentId, MasterySnapshot> load(UUID playerId);

    MasteryMutationCommit mutate(UUID playerId, ContentId masteryId, OperationId operationId,
                                 long awardedXp, UnaryOperator<MasterySnapshot> mutation);
}
