package com.branz.mmorpg.social.downed;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record DownedTransition(
        DownedEncounterRuntime runtime,
        Set<CharacterId> newlyDowned,
        Set<CharacterId> newlyDead,
        Map<CharacterId, Double> revivedHealthRatios,
        boolean changed) {
    public DownedTransition {
        Objects.requireNonNull(runtime, "runtime");
        newlyDowned = Set.copyOf(Objects.requireNonNull(newlyDowned, "newlyDowned"));
        newlyDead = Set.copyOf(Objects.requireNonNull(newlyDead, "newlyDead"));
        revivedHealthRatios =
                Map.copyOf(Objects.requireNonNull(revivedHealthRatios, "revivedHealthRatios"));
        if (!changed
                && (!newlyDowned.isEmpty()
                        || !newlyDead.isEmpty()
                        || !revivedHealthRatios.isEmpty())) {
            throw new IllegalArgumentException("unchanged transition cannot emit effects");
        }
    }

    static DownedTransition unchanged(DownedEncounterRuntime runtime) {
        return new DownedTransition(runtime, Set.of(), Set.of(), Map.of(), false);
    }
}
