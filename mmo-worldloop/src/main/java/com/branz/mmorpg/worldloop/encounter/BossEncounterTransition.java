package com.branz.mmorpg.worldloop.encounter;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;
import java.util.Set;

/** State plus effects that must be committed by the live adapter exactly once. */
public record BossEncounterTransition(
        BossEncounterRuntime runtime,
        Set<CharacterId> flaskRestoreParticipants,
        boolean rewardReconciliationRequested,
        boolean changed) {
    public BossEncounterTransition {
        Objects.requireNonNull(runtime, "runtime");
        flaskRestoreParticipants =
                Set.copyOf(
                        Objects.requireNonNull(
                                flaskRestoreParticipants, "flaskRestoreParticipants"));
        if (!changed && (!flaskRestoreParticipants.isEmpty() || rewardReconciliationRequested)) {
            throw new IllegalArgumentException("an unchanged transition cannot request effects");
        }
    }

    public static BossEncounterTransition unchanged(BossEncounterRuntime runtime) {
        return new BossEncounterTransition(runtime, Set.of(), false, false);
    }
}
