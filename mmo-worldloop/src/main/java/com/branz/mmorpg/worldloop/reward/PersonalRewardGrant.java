package com.branz.mmorpg.worldloop.reward;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;
import java.util.UUID;

public record PersonalRewardGrant(CharacterId characterId, UUID grantId, long rollSeed) {
    public PersonalRewardGrant {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(grantId, "grantId");
    }
}
