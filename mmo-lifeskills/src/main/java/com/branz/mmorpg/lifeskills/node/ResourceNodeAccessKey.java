package com.branz.mmorpg.lifeskills.node;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;
import java.util.Optional;

/** Selects either the one shared slot or a character's personal extraction slot. */
public record ResourceNodeAccessKey(Optional<CharacterId> owner) {
    public ResourceNodeAccessKey {
        owner = Objects.requireNonNull(owner, "owner");
    }

    public static ResourceNodeAccessKey shared() {
        return new ResourceNodeAccessKey(Optional.empty());
    }

    public static ResourceNodeAccessKey personal(CharacterId owner) {
        return new ResourceNodeAccessKey(Optional.of(Objects.requireNonNull(owner, "owner")));
    }
}
