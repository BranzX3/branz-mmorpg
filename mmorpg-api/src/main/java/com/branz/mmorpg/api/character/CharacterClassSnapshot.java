package com.branz.mmorpg.api.character;

import com.branz.mmorpg.api.operation.OperationId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Authoritative immutable view of a player's permanent class selection. */
public record CharacterClassSnapshot(
        UUID playerId,
        CharacterClassState state,
        Optional<CharacterClassId> classId,
        Optional<Instant> selectedAt,
        Optional<OperationId> selectionOperationId,
        int classSchemaVersion,
        long profileRevision) {
    public CharacterClassSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(state, "state");
        classId = Objects.requireNonNull(classId, "classId");
        selectedAt = Objects.requireNonNull(selectedAt, "selectedAt");
        selectionOperationId = Objects.requireNonNull(selectionOperationId, "selectionOperationId");
        if (profileRevision < 0) throw new IllegalArgumentException("profile revision must not be negative");
        boolean selected = state == CharacterClassState.CLASS_SELECTED;
        if (selected != (classId.isPresent() && selectedAt.isPresent() && selectionOperationId.isPresent())) {
            throw new IllegalArgumentException("selected class snapshot is incomplete");
        }
        if (selected && classSchemaVersion < 1) {
            throw new IllegalArgumentException("selected class needs a schema version");
        }
    }

    public static CharacterClassSnapshot unselected(UUID playerId, long profileRevision) {
        return new CharacterClassSnapshot(playerId, CharacterClassState.CLASS_UNSELECTED,
                Optional.empty(), Optional.empty(), Optional.empty(), 0, profileRevision);
    }
}
