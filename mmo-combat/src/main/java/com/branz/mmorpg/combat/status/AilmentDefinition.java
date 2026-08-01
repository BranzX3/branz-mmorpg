package com.branz.mmorpg.combat.status;

import java.util.Objects;
import java.util.Set;

/** Data-driven buildup/active contract; immediate physical CC remains outside this model. */
public record AilmentDefinition(
        AilmentType type,
        double buildupMaximum,
        int buildupDecayDelayTicks,
        double buildupDecayPerTick,
        int activeDurationTicks,
        AilmentReapplication reapplication,
        int maximumTier,
        String resistanceChannel,
        Set<String> cleanseTags,
        AilmentPersistence persistence,
        double pveMultiplier,
        double pvpMultiplier,
        String visualCue,
        String audioCue) {
    public AilmentDefinition {
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(buildupMaximum)
                || buildupMaximum <= 0
                || buildupDecayDelayTicks < 0
                || !Double.isFinite(buildupDecayPerTick)
                || buildupDecayPerTick < 0
                || activeDurationTicks < 1) {
            throw new IllegalArgumentException("invalid ailment buildup/duration fields");
        }
        Objects.requireNonNull(reapplication, "reapplication");
        if (maximumTier < 1
                || (reapplication != AilmentReapplication.INTENSIFY && maximumTier != 1)) {
            throw new IllegalArgumentException("maximumTier must match reapplication behavior");
        }
        resistanceChannel = requireText(resistanceChannel, "resistanceChannel");
        cleanseTags = Set.copyOf(Objects.requireNonNull(cleanseTags, "cleanseTags"));
        if (cleanseTags.isEmpty()
                || cleanseTags.stream().anyMatch(tag -> tag == null || tag.isBlank())) {
            throw new IllegalArgumentException("cleanseTags must contain non-blank entries");
        }
        Objects.requireNonNull(persistence, "persistence");
        if (!Double.isFinite(pveMultiplier)
                || pveMultiplier <= 0
                || !Double.isFinite(pvpMultiplier)
                || pvpMultiplier <= 0) {
            throw new IllegalArgumentException("ailment profile multipliers must be positive");
        }
        visualCue = requireText(visualCue, "visualCue");
        audioCue = requireText(audioCue, "audioCue");
    }

    public AilmentDefinition(
            AilmentType type,
            double buildupMaximum,
            int buildupDecayDelayTicks,
            double buildupDecayPerTick,
            int activeDurationTicks,
            AilmentReapplication reapplication,
            int maximumTier,
            String resistanceChannel,
            Set<String> cleanseTags,
            AilmentPersistence persistence) {
        this(
                type,
                buildupMaximum,
                buildupDecayDelayTicks,
                buildupDecayPerTick,
                activeDurationTicks,
                reapplication,
                maximumTier,
                resistanceChannel,
                cleanseTags,
                persistence,
                1,
                1,
                "status.generic",
                "status.generic");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
