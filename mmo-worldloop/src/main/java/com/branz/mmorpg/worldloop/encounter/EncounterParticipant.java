package com.branz.mmorpg.worldloop.encounter;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;

public record EncounterParticipant(
        CharacterId characterId, EncounterParticipantStatus status, long graceDeadlineTick) {
    public static final long NO_GRACE_DEADLINE = -1;

    public EncounterParticipant {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(status, "status");
        boolean graceStatus =
                status == EncounterParticipantStatus.DISCONNECTED_GRACE
                        || status == EncounterParticipantStatus.OUTSIDE_GRACE;
        if (graceStatus != (graceDeadlineTick >= 0)) {
            throw new IllegalArgumentException(
                    "only disconnected or outside participants have a grace deadline");
        }
    }

    public static EncounterParticipant active(CharacterId characterId) {
        return new EncounterParticipant(
                characterId, EncounterParticipantStatus.ACTIVE, NO_GRACE_DEADLINE);
    }

    public EncounterParticipant withStatus(
            EncounterParticipantStatus replacement, long replacementDeadlineTick) {
        return new EncounterParticipant(characterId, replacement, replacementDeadlineTick);
    }
}
