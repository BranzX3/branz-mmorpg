package com.branz.mmorpg.persistence.progression;

import com.branz.mmorpg.api.identity.CharacterId;
import java.time.Instant;
import java.util.Objects;

public record RenownRecord(CharacterId characterId, long renown, long version, Instant updatedAt) {
    public RenownRecord {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (renown < 0 || version < 1) {
            throw new IllegalArgumentException("persisted Renown values are invalid");
        }
    }
}
