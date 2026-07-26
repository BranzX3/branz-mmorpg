package com.branz.mmorpg.core.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.social.PartyRepository;
import com.branz.mmorpg.api.social.PartySnapshot;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultPartyServiceTest {
    private final FixedGameClock clock =
            FixedGameClock.at("2026-07-26T00:00:00Z");
    private final FakeRepository repository = new FakeRepository();
    private final DefaultPartyService service = new DefaultPartyService(repository, clock);
    private final UUID leader = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private final UUID member = UUID.fromString("80000000-0000-0000-0000-000000000002");

    @Test
    void stablePartySupportsInvitationAcceptanceAndDeterministicLeaderHandoff() {
        PartySnapshot party = service.create(leader);
        service.invite(leader, member);
        PartySnapshot accepted = service.accept(member, party.partyId());
        assertEquals(2, accepted.members().size());
        PartySnapshot afterLeave = service.leave(leader).orElseThrow();
        assertEquals(member, afterLeave.leaderId());
        assertEquals(party.partyId(), afterLeave.partyId());
    }

    @Test
    void expiredInvitationAndDuplicateMembershipFailClosed() {
        PartySnapshot party = service.create(leader);
        service.invite(leader, member);
        clock.advance(Duration.ofMinutes(3));
        assertThrows(IllegalStateException.class,
                () -> service.accept(member, party.partyId()));
        assertThrows(IllegalStateException.class, () -> service.create(leader));
    }

    @Test
    void finalMemberLeaveDeletesParty() {
        PartySnapshot party = service.create(leader);
        assertFalse(service.leave(leader).isPresent());
        assertFalse(repository.find(party.partyId()).isPresent());
    }

    private static final class FakeRepository implements PartyRepository {
        private final Map<UUID, PartySnapshot> parties = new HashMap<>();
        @Override public PartySnapshot insert(PartySnapshot party) {
            if (findByMember(party.leaderId()).isPresent()) throw new IllegalStateException();
            parties.put(party.partyId(), party);
            return party;
        }
        @Override public Optional<PartySnapshot> find(UUID partyId) {
            return Optional.ofNullable(parties.get(partyId));
        }
        @Override public Optional<PartySnapshot> findByMember(UUID playerId) {
            return parties.values().stream()
                    .filter(party -> party.members().contains(playerId)).findFirst();
        }
        @Override public PartySnapshot save(PartySnapshot party) {
            parties.put(party.partyId(), party);
            return party;
        }
        @Override public boolean delete(UUID partyId, long expectedRevision) {
            return parties.remove(partyId) != null;
        }
    }
}
