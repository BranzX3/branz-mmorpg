package com.branz.mmorpg.api.character;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.operation.OperationId;
import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Blocking persistence port for atomic class XP/tree mutations. */
public interface CharacterClassProgressionRepository {
    CharacterClassProgress load(UUID playerId, ContentId classId, int treeRevision, Instant now);

    ClassProgressionMutationCommit mutate(UUID playerId, ContentId classId, int treeRevision,
                                          OperationId operationId, String auditAction,
                                          UnaryOperator<CharacterClassProgress> mutation);
}
