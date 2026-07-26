package com.branz.mmorpg.api.character;

import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.player.SessionToken;
import java.util.Objects;
import java.util.UUID;

public record CharacterClassSelectionRequest(
        OperationId operationId,
        UUID playerId,
        SessionToken sessionToken,
        CharacterClassId selectedClassId,
        long expectedProfileRevision,
        long expectedContentRevision,
        boolean permanentChoiceConfirmed) {
    public CharacterClassSelectionRequest {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sessionToken, "sessionToken");
        Objects.requireNonNull(selectedClassId, "selectedClassId");
        if (!playerId.equals(operationId.playerUuid()) || !playerId.equals(sessionToken.playerId())) {
            throw new IllegalArgumentException("operation, player, and session identities must match");
        }
        if (expectedProfileRevision < 0 || expectedContentRevision < 1) {
            throw new IllegalArgumentException("expected revisions are invalid");
        }
    }
}
