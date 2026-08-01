package com.branz.mmorpg.social.party;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PartyReadyCheck(
        UUID checkId,
        CharacterId startedBy,
        long startedTick,
        long expiresTick,
        Map<CharacterId, Boolean> responses) {
    public PartyReadyCheck {
        Objects.requireNonNull(checkId, "checkId");
        Objects.requireNonNull(startedBy, "startedBy");
        responses = Map.copyOf(Objects.requireNonNull(responses, "responses"));
        if (startedTick < 0 || expiresTick <= startedTick) {
            throw new IllegalArgumentException("invalid ready check");
        }
    }
}
