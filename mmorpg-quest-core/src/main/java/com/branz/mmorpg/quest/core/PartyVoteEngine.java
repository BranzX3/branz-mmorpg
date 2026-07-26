package com.branz.mmorpg.quest.core;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PartyVoteEngine {
    public enum Policy { LEADER_DECIDES, MAJORITY_VOTE, UNANIMOUS, INDIVIDUAL_BRANCH }
    public record Vote(UUID voteId, Policy policy, UUID leaderSnapshot,
                       Set<UUID> eligibleSnapshot, Set<String> choices,
                       Map<UUID, String> ballots, Instant expiresAt) {
        public Vote {
            eligibleSnapshot = Set.copyOf(eligibleSnapshot);
            choices = Set.copyOf(choices);
            ballots = Map.copyOf(ballots);
        }
    }

    public Vote begin(Policy policy, UUID leader, Set<UUID> eligible,
                      Set<String> choices, Instant expiresAt) {
        if (!eligible.contains(leader) || eligible.isEmpty() || choices.isEmpty()) {
            throw new IllegalArgumentException("invalid vote snapshot");
        }
        return new Vote(UUID.randomUUID(), policy, leader, eligible,
                choices, Map.of(), expiresAt);
    }

    public Vote vote(Vote before, UUID player, String choice, Instant now) {
        if (!before.expiresAt().isAfter(now)
                || !before.eligibleSnapshot().contains(player)
                || !before.choices().contains(choice)) {
            throw new IllegalStateException("vote is closed or ballot invalid");
        }
        HashMap<UUID, String> ballots = new HashMap<>(before.ballots());
        ballots.put(player, choice);
        return new Vote(before.voteId(), before.policy(), before.leaderSnapshot(),
                before.eligibleSnapshot(), before.choices(), ballots, before.expiresAt());
    }

    public Optional<String> result(Vote vote, Instant now, String tieChoice) {
        if (vote.policy() == Policy.INDIVIDUAL_BRANCH) return Optional.empty();
        if (vote.policy() == Policy.LEADER_DECIDES) {
            return Optional.ofNullable(vote.ballots().get(vote.leaderSnapshot()));
        }
        if (vote.policy() == Policy.UNANIMOUS) {
            if (vote.ballots().size() < vote.eligibleSnapshot().size()
                    && vote.expiresAt().isAfter(now)) return Optional.empty();
            return vote.ballots().values().stream().distinct().count() == 1
                    ? vote.ballots().values().stream().findFirst() : Optional.of(tieChoice);
        }
        if (vote.ballots().size() < vote.eligibleSnapshot().size()
                && vote.expiresAt().isAfter(now)) return Optional.empty();
        return vote.ballots().values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .findFirst().filter(entry -> vote.ballots().values().stream()
                        .filter(entry.getKey()::equals).count()
                        > vote.eligibleSnapshot().size() / 2)
                .map(Map.Entry::getKey).or(() -> Optional.of(tieChoice));
    }
}
