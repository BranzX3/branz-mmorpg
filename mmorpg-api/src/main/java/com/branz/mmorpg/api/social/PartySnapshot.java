package com.branz.mmorpg.api.social;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record PartySnapshot(
        UUID partyId,
        UUID leaderId,
        Set<UUID> members,
        Map<UUID, Instant> invitations,
        int maximumMembers,
        double rewardRange,
        boolean rewardsRequireSameWorld,
        Instant createdAt,
        long revision) {
    public PartySnapshot {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(leaderId, "leaderId");
        members = Set.copyOf(members);
        invitations = Map.copyOf(invitations);
        Objects.requireNonNull(createdAt, "createdAt");
        if (!members.contains(leaderId) || maximumMembers < 2
                || members.size() > maximumMembers || !Double.isFinite(rewardRange)
                || rewardRange <= 0 || revision < 0) {
            throw new IllegalArgumentException("invalid party snapshot");
        }
    }
}
