package com.branz.mmorpg.api.social;

import java.util.Optional;
import java.util.UUID;

public interface PartyRepository {
    PartySnapshot insert(PartySnapshot party);
    Optional<PartySnapshot> find(UUID partyId);
    Optional<PartySnapshot> findByMember(UUID playerId);
    PartySnapshot save(PartySnapshot party);
    boolean delete(UUID partyId, long expectedRevision);
}
