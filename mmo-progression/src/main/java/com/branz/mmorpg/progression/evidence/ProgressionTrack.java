package com.branz.mmorpg.progression.evidence;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Locale;
import java.util.Objects;

/** Stable internal identity for one mastery discipline or conditioning axis. */
public record ProgressionTrack(DefinitionId id, ProgressionTrackType type) {
    private static final String MASTERY_PREFIX = "mastery.";
    private static final String CONDITIONING_PREFIX = "conditioning.";

    public ProgressionTrack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        String requiredPrefix =
                type == ProgressionTrackType.DISCIPLINE_MASTERY
                        ? MASTERY_PREFIX
                        : CONDITIONING_PREFIX;
        if (!id.value().startsWith(requiredPrefix)) {
            throw new IllegalArgumentException(
                    type + " track ID must start with " + requiredPrefix);
        }
    }

    public static ProgressionTrack mastery(String discipline) {
        Objects.requireNonNull(discipline, "discipline");
        if (discipline.isBlank()) {
            throw new IllegalArgumentException("discipline must not be blank");
        }
        return new ProgressionTrack(
                DefinitionId.of(MASTERY_PREFIX + discipline),
                ProgressionTrackType.DISCIPLINE_MASTERY);
    }

    public static ProgressionTrack conditioning(BodyConditioningAxis axis) {
        Objects.requireNonNull(axis, "axis");
        return new ProgressionTrack(
                DefinitionId.of(CONDITIONING_PREFIX + axis.name().toLowerCase(Locale.ROOT)),
                ProgressionTrackType.BODY_CONDITIONING);
    }
}
