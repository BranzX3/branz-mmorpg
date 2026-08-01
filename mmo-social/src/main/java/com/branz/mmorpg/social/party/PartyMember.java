package com.branz.mmorpg.social.party;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;

public record PartyMember(
        CharacterId characterId,
        PartyMemberStatus status,
        long joinedOrder,
        long disconnectDeadlineTick) {
    public static final long NO_DEADLINE = -1;

    public PartyMember {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(status, "status");
        if (joinedOrder < 0
                || (status == PartyMemberStatus.DISCONNECTED_GRACE)
                        != (disconnectDeadlineTick >= 0)) {
            throw new IllegalArgumentException("invalid party member");
        }
    }

    static PartyMember online(CharacterId characterId, long joinedOrder) {
        return new PartyMember(characterId, PartyMemberStatus.ONLINE, joinedOrder, NO_DEADLINE);
    }
}
