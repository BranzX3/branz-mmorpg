package com.branz.mmorpg.social.pvp;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

public record PvpMatchResult(
        OptionalInt winningTeam, Set<CharacterId> defeated, PvpCompletionReason reason) {
    public PvpMatchResult {
        winningTeam = Objects.requireNonNull(winningTeam, "winningTeam");
        defeated = Set.copyOf(Objects.requireNonNull(defeated, "defeated"));
        Objects.requireNonNull(reason, "reason");
        if (winningTeam.isPresent() && (winningTeam.getAsInt() < 0 || winningTeam.getAsInt() > 1)) {
            throw new IllegalArgumentException("winning team must be 0 or 1");
        }
    }
}
