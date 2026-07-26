package com.branz.mmorpg.api.social;

import java.util.Optional;
import java.util.UUID;

public interface PartyService {
    PartySnapshot create(UUID leaderId);
    PartySnapshot invite(UUID leaderId, UUID playerId);
    PartySnapshot accept(UUID playerId, UUID partyId);
    Optional<PartySnapshot> leave(UUID playerId);
    PartySnapshot kick(UUID leaderId, UUID playerId);
    PartySnapshot transferLeadership(UUID leaderId, UUID newLeaderId);
    Optional<PartySnapshot> party(UUID playerId);
}
