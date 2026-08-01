package com.branz.mmorpg.social.party;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record PartyTransition(
        PartyRuntime runtime,
        Set<CharacterId> joined,
        Set<CharacterId> removed,
        Optional<CharacterId> newLeader,
        Optional<Boolean> readyCheckResult,
        boolean changed) {
    public PartyTransition {
        Objects.requireNonNull(runtime, "runtime");
        joined = Set.copyOf(Objects.requireNonNull(joined, "joined"));
        removed = Set.copyOf(Objects.requireNonNull(removed, "removed"));
        newLeader = Objects.requireNonNull(newLeader, "newLeader");
        readyCheckResult = Objects.requireNonNull(readyCheckResult, "readyCheckResult");
        if (!changed
                && (!joined.isEmpty()
                        || !removed.isEmpty()
                        || newLeader.isPresent()
                        || readyCheckResult.isPresent())) {
            throw new IllegalArgumentException("unchanged transition cannot emit effects");
        }
    }

    static PartyTransition unchanged(PartyRuntime runtime) {
        return new PartyTransition(
                runtime, Set.of(), Set.of(), Optional.empty(), Optional.empty(), false);
    }
}
