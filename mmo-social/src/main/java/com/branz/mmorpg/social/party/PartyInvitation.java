package com.branz.mmorpg.social.party;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;

public record PartyInvitation(
        CharacterId targetId, CharacterId invitedBy, long createdTick, long expiresTick) {
    public PartyInvitation {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(invitedBy, "invitedBy");
        if (targetId.equals(invitedBy) || createdTick < 0 || expiresTick <= createdTick) {
            throw new IllegalArgumentException("invalid party invitation");
        }
    }
}
