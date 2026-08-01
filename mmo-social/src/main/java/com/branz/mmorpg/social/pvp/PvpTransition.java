package com.branz.mmorpg.social.pvp;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record PvpTransition(
        PvpMatchRuntime runtime,
        boolean newlyActive,
        Set<CharacterId> newlyDefeated,
        Optional<PvpMatchResult> completion,
        boolean changed) {
    public PvpTransition {
        Objects.requireNonNull(runtime, "runtime");
        newlyDefeated = Set.copyOf(Objects.requireNonNull(newlyDefeated, "newlyDefeated"));
        completion = Objects.requireNonNull(completion, "completion");
        if (!changed && (newlyActive || !newlyDefeated.isEmpty() || completion.isPresent())) {
            throw new IllegalArgumentException("unchanged PvP transition cannot emit effects");
        }
    }

    static PvpTransition unchanged(PvpMatchRuntime runtime) {
        return new PvpTransition(runtime, false, Set.of(), Optional.empty(), false);
    }
}
