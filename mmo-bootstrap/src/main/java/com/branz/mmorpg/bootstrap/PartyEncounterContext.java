package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import java.util.Objects;
import java.util.Set;

/** Active boss-attempt identity exposed to party-PvE runtime adapters. */
record PartyEncounterContext(EncounterId encounterId, int attempt, Set<CharacterId> participants) {
    PartyEncounterContext {
        Objects.requireNonNull(encounterId, "encounterId");
        participants = Set.copyOf(Objects.requireNonNull(participants, "participants"));
        if (attempt < 1 || participants.size() < 2 || participants.size() > 5) {
            throw new IllegalArgumentException("party encounter context is invalid");
        }
    }
}
