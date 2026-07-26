package com.branz.mmorpg.api.encounter;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record EncounterDefinition(
        ContentId id,
        String displayName,
        Mode mode,
        ContentId bossMobId,
        List<Phase> phases,
        double arenaRadius,
        long preparationMillis,
        long wipeGraceMillis,
        long enrageMillis,
        int minimumPlayers,
        int maximumPlayers,
        double minimumContribution,
        PartyPolicy partyPolicy,
        boolean checkpointsAllowed,
        ContentId rewardLootTableId) implements ContentDefinition {
    public enum Mode { PUBLIC_WORLD, PRIVATE_PARTY }
    public enum PartyPolicy { SNAPSHOT_AT_START, INDIVIDUAL }

    public EncounterDefinition {
        Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(bossMobId, "bossMobId");
        phases = List.copyOf(phases);
        Objects.requireNonNull(partyPolicy, "partyPolicy");
        Objects.requireNonNull(rewardLootTableId, "rewardLootTableId");
        if (displayName.isEmpty() || phases.size() < 1
                || !Double.isFinite(arenaRadius) || arenaRadius <= 0
                || preparationMillis < 0 || wipeGraceMillis < 1 || enrageMillis < 1
                || minimumPlayers < 1 || maximumPlayers < minimumPlayers
                || !Double.isFinite(minimumContribution) || minimumContribution < 0) {
            throw new IllegalArgumentException("invalid encounter " + id);
        }
        double previous = 1.01;
        for (Phase phase : phases) {
            if (phase.healthFractionThreshold() >= previous) {
                throw new IllegalArgumentException("phase thresholds must strictly descend");
            }
            previous = phase.healthFractionThreshold();
        }
        if (phases.getLast().healthFractionThreshold() != 0) {
            throw new IllegalArgumentException("final phase threshold must be zero");
        }
    }

    @Override public ContentType type() { return ContentType.ENCOUNTER; }

    public record Phase(
            String id,
            double healthFractionThreshold,
            Set<ContentId> abilityIds,
            Set<ContentId> addMobIds,
            double pressureMultiplier) {
        public Phase {
            id = Objects.requireNonNull(id, "id").trim();
            abilityIds = Set.copyOf(abilityIds);
            addMobIds = Set.copyOf(addMobIds);
            if (id.isEmpty() || !Double.isFinite(healthFractionThreshold)
                    || healthFractionThreshold < 0 || healthFractionThreshold > 1
                    || abilityIds.isEmpty() || !Double.isFinite(pressureMultiplier)
                    || pressureMultiplier < 1 || pressureMultiplier > 100) {
                throw new IllegalArgumentException("invalid encounter phase");
            }
        }
    }
}
