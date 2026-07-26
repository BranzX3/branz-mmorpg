package com.branz.mmorpg.api.character;

import com.branz.mmorpg.api.operation.OperationId;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

/** Blocking persistence port; implementations commit class, starter plan, and audit atomically. */
public interface CharacterClassSelectionRepository {
    default Optional<CharacterClassSelectionResult> find(UUID playerId, OperationId operationId) {
        return Optional.empty();
    }

    CharacterClassSelectionResult select(
            UUID playerId,
            long expectedProfileRevision,
            OperationId operationId,
            CharacterClassDefinition definition,
            long contentRevision,
            Instant selectedAt);
}
