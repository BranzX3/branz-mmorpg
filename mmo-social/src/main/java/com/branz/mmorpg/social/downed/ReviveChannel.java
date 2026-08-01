package com.branz.mmorpg.social.downed;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;
import java.util.UUID;

public record ReviveChannel(
        UUID channelId,
        CharacterId reviverId,
        CharacterId targetId,
        long startedTick,
        long commitTick) {
    public ReviveChannel {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(reviverId, "reviverId");
        Objects.requireNonNull(targetId, "targetId");
        if (reviverId.equals(targetId)) {
            throw new IllegalArgumentException("a participant cannot revive itself");
        }
        if (startedTick < 0 || commitTick <= startedTick) {
            throw new IllegalArgumentException("revive channel tick window must be positive");
        }
    }
}
