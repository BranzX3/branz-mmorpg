package com.branz.mmorpg.persistence.lease;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.SessionId;
import java.time.Instant;
import java.util.Objects;

public record CharacterLease(
        CharacterId characterId,
        ServerInstanceId serverInstanceId,
        SessionId sessionId,
        long version,
        Instant acquiredAt,
        Instant heartbeatAt,
        Instant expiresAt) {
    public CharacterLease {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(serverInstanceId, "serverInstanceId");
        Objects.requireNonNull(sessionId, "sessionId");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        Objects.requireNonNull(heartbeatAt, "heartbeatAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(heartbeatAt)) {
            throw new IllegalArgumentException("expiry must be after heartbeat");
        }
    }
}
