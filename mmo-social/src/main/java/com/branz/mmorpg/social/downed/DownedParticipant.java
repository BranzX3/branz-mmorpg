package com.branz.mmorpg.social.downed;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;

public record DownedParticipant(
        CharacterId characterId,
        EncounterLifeState lifeState,
        boolean reviveConsumed,
        long downedDeadlineTick,
        long protectionUntilTick) {
    public static final long NO_DEADLINE = -1;

    public DownedParticipant {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(lifeState, "lifeState");
        if ((lifeState == EncounterLifeState.DOWNED) != (downedDeadlineTick >= 0)) {
            throw new IllegalArgumentException("only downed participants have a downed deadline");
        }
        if (lifeState != EncounterLifeState.ACTIVE && protectionUntilTick >= 0) {
            throw new IllegalArgumentException(
                    "only active participants can have revive protection");
        }
    }

    static DownedParticipant active(CharacterId characterId) {
        return new DownedParticipant(
                characterId, EncounterLifeState.ACTIVE, false, NO_DEADLINE, NO_DEADLINE);
    }

    boolean protectedAt(long currentTick) {
        return protectionUntilTick > currentTick;
    }
}
