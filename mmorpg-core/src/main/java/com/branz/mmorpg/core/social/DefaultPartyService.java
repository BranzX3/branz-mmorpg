package com.branz.mmorpg.core.social;

import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.api.social.PartyRepository;
import com.branz.mmorpg.api.social.PartyService;
import com.branz.mmorpg.api.social.PartySnapshot;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

public final class DefaultPartyService implements PartyService {
    private static final int MAXIMUM_MEMBERS = 5;
    private static final double REWARD_RANGE = 64;
    private static final Duration INVITATION_TTL = Duration.ofMinutes(2);
    private final PartyRepository repository;
    private final GameClock clock;

    public DefaultPartyService(PartyRepository repository, GameClock clock) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override public PartySnapshot create(UUID leaderId) {
        if (repository.findByMember(leaderId).isPresent()) {
            throw new IllegalStateException("player is already in a party");
        }
        return repository.insert(new PartySnapshot(
                UUID.randomUUID(), leaderId, java.util.Set.of(leaderId), java.util.Map.of(),
                MAXIMUM_MEMBERS, REWARD_RANGE, true, clock.now(), 0));
    }

    @Override public PartySnapshot invite(UUID leaderId, UUID playerId) {
        PartySnapshot party = requireLeader(leaderId);
        if (party.members().contains(playerId)
                || repository.findByMember(playerId).isPresent()) {
            throw new IllegalStateException("player is already in a party");
        }
        if (party.members().size() >= party.maximumMembers()) {
            throw new IllegalStateException("party is full");
        }
        HashMap<UUID, java.time.Instant> invitations = activeInvitations(party);
        invitations.put(playerId, clock.now().plus(INVITATION_TTL));
        return repository.save(copy(party, party.leaderId(), party.members(), invitations));
    }

    @Override public PartySnapshot accept(UUID playerId, UUID partyId) {
        if (repository.findByMember(playerId).isPresent()) {
            throw new IllegalStateException("player is already in a party");
        }
        PartySnapshot party = repository.find(partyId).orElseThrow(
                () -> new IllegalArgumentException("unknown party " + partyId));
        java.time.Instant expiry = party.invitations().get(playerId);
        if (expiry == null || !expiry.isAfter(clock.now())) {
            throw new IllegalStateException("party invitation expired");
        }
        if (party.members().size() >= party.maximumMembers()) {
            throw new IllegalStateException("party is full");
        }
        HashSet<UUID> members = new HashSet<>(party.members());
        members.add(playerId);
        HashMap<UUID, java.time.Instant> invitations = activeInvitations(party);
        invitations.remove(playerId);
        return repository.save(copy(party, party.leaderId(), members, invitations));
    }

    @Override public Optional<PartySnapshot> leave(UUID playerId) {
        PartySnapshot party = repository.findByMember(playerId).orElseThrow(
                () -> new IllegalStateException("player is not in a party"));
        HashSet<UUID> members = new HashSet<>(party.members());
        members.remove(playerId);
        if (members.isEmpty()) {
            repository.delete(party.partyId(), party.revision());
            return Optional.empty();
        }
        UUID leader = party.leaderId().equals(playerId)
                ? members.stream().sorted().findFirst().orElseThrow() : party.leaderId();
        return Optional.of(repository.save(copy(
                party, leader, members, activeInvitations(party))));
    }

    @Override public PartySnapshot kick(UUID leaderId, UUID playerId) {
        PartySnapshot party = requireLeader(leaderId);
        if (leaderId.equals(playerId) || !party.members().contains(playerId)) {
            throw new IllegalArgumentException("invalid kick target");
        }
        HashSet<UUID> members = new HashSet<>(party.members());
        members.remove(playerId);
        return repository.save(copy(
                party, leaderId, members, activeInvitations(party)));
    }

    @Override public PartySnapshot transferLeadership(UUID leaderId, UUID newLeaderId) {
        PartySnapshot party = requireLeader(leaderId);
        if (!party.members().contains(newLeaderId)) {
            throw new IllegalArgumentException("new leader is not a member");
        }
        return repository.save(copy(
                party, newLeaderId, party.members(), activeInvitations(party)));
    }

    @Override public Optional<PartySnapshot> party(UUID playerId) {
        return repository.findByMember(playerId);
    }

    private PartySnapshot requireLeader(UUID playerId) {
        PartySnapshot party = repository.findByMember(playerId).orElseThrow(
                () -> new IllegalStateException("player is not in a party"));
        if (!party.leaderId().equals(playerId)) {
            throw new IllegalStateException("only the party leader may do that");
        }
        return party;
    }

    private HashMap<UUID, java.time.Instant> activeInvitations(PartySnapshot party) {
        HashMap<UUID, java.time.Instant> result = new HashMap<>();
        party.invitations().forEach((player, expiry) -> {
            if (expiry.isAfter(clock.now())) result.put(player, expiry);
        });
        return result;
    }

    private static PartySnapshot copy(
            PartySnapshot source, UUID leader, java.util.Set<UUID> members,
            java.util.Map<UUID, java.time.Instant> invitations) {
        return new PartySnapshot(source.partyId(), leader, members, invitations,
                source.maximumMembers(), source.rewardRange(),
                source.rewardsRequireSameWorld(), source.createdAt(), source.revision() + 1);
    }
}
