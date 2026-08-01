package com.branz.mmorpg.social.pvp;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;

public record PvpParticipant(
        CharacterId characterId,
        int team,
        PvpParticipantStatus status,
        long disconnectExpiresTick) {
    public PvpParticipant {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(status, "status");
        if (team < 0 || team > 1) {
            throw new IllegalArgumentException("PvP team must be 0 or 1");
        }
        if ((status == PvpParticipantStatus.DISCONNECTED_GRACE) != (disconnectExpiresTick >= 0)) {
            throw new IllegalArgumentException("disconnect expiry must match participant status");
        }
    }

    static PvpParticipant ready(CharacterId characterId, int team) {
        return new PvpParticipant(characterId, team, PvpParticipantStatus.READY, -1);
    }
}
