package com.branz.mmorpg.api.mob;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record MobDefinition(
        ContentId id,
        String displayName,
        Map<String, Double> baseStats,
        Scaling scaling,
        String faction,
        TargetPolicy targetPolicy,
        Navigation navigation,
        List<MobAbilityDefinition> abilities,
        double aggroRange,
        double leashRange,
        long resetMillis,
        Optional<ContentId> homeRegionId,
        Set<ContentId> statusImmunities,
        Map<ContentId, Double> statusResistances,
        ContentId lootTableId,
        double minimumContribution,
        Presentation presentation) implements ContentDefinition {

    public enum TargetPolicy { HOSTILE_PLAYERS, RETALIATE, OBJECTIVE }

    public MobDefinition {
        Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        baseStats = Map.copyOf(baseStats);
        Objects.requireNonNull(scaling, "scaling");
        faction = Objects.requireNonNull(faction, "faction").trim();
        Objects.requireNonNull(targetPolicy, "targetPolicy");
        Objects.requireNonNull(navigation, "navigation");
        abilities = List.copyOf(abilities);
        Objects.requireNonNull(homeRegionId, "homeRegionId");
        statusImmunities = Set.copyOf(statusImmunities);
        statusResistances = Map.copyOf(statusResistances);
        Objects.requireNonNull(lootTableId, "lootTableId");
        Objects.requireNonNull(presentation, "presentation");
        baseStats.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null
                    || !Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("invalid mob base stat");
            }
        });
        statusResistances.forEach((status, resistance) -> {
            if (status == null || resistance == null || !Double.isFinite(resistance)
                    || resistance < 0 || resistance > 1) {
                throw new IllegalArgumentException("invalid status resistance");
            }
        });
        if (displayName.isEmpty() || faction.isEmpty() || abilities.isEmpty()
                || !Double.isFinite(aggroRange) || aggroRange <= 0
                || !Double.isFinite(leashRange) || leashRange < aggroRange
                || resetMillis < 1 || !Double.isFinite(minimumContribution)
                || minimumContribution < 0) {
            throw new IllegalArgumentException("invalid mob definition " + id);
        }
    }

    @Override public ContentType type() { return ContentType.MOB; }

    public record Scaling(double healthPerLevel, double powerPerLevel,
                          double maximumMultiplier) {
        public Scaling {
            if (!Double.isFinite(healthPerLevel) || healthPerLevel < 0
                    || !Double.isFinite(powerPerLevel) || powerPerLevel < 0
                    || !Double.isFinite(maximumMultiplier)
                    || maximumMultiplier < 1 || maximumMultiplier > 100) {
                throw new IllegalArgumentException("invalid mob scaling");
            }
        }
    }

    public record Navigation(double movementSpeed, long decisionIntervalMillis,
                             long pathRequestIntervalMillis, boolean canSwim) {
        public Navigation {
            if (!Double.isFinite(movementSpeed) || movementSpeed <= 0
                    || decisionIntervalMillis < 50 || pathRequestIntervalMillis < 50) {
                throw new IllegalArgumentException("invalid mob navigation");
            }
        }
    }

    public record Presentation(String entityType, Optional<String> modelId) {
        public Presentation {
            entityType = Objects.requireNonNull(entityType, "entityType").trim();
            Objects.requireNonNull(modelId, "modelId");
            if (entityType.isEmpty()) throw new IllegalArgumentException("empty entity type");
        }
    }
}
