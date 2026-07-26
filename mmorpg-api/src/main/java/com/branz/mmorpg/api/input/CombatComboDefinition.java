package com.branz.mmorpg.api.input;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Declarative bounded combo finite-state machine. */
public record CombatComboDefinition(
        ContentId id,
        Set<String> requiredTags,
        List<Step> steps,
        long resetTimeoutMillis,
        int priority,
        boolean consumesInput,
        ContentId resultSkillId) implements ContentDefinition {

    public CombatComboDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requiredTags, "requiredTags");
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(resultSkillId, "resultSkillId");
        requiredTags = Set.copyOf(requiredTags);
        steps = List.copyOf(steps);
        if (steps.size() < 2 || steps.size() > 8 || resetTimeoutMillis < 1
                || resetTimeoutMillis > 2_000) {
            throw new IllegalArgumentException(id + ": combo bounds are invalid");
        }
    }

    @Override public ContentType type() { return ContentType.COMBAT_COMBO; }

    public record Step(CombatInputKey input, long minimumDelayMillis, long maximumDelayMillis) {
        public Step {
            Objects.requireNonNull(input, "input");
            if (minimumDelayMillis < 0 || maximumDelayMillis < minimumDelayMillis
                    || maximumDelayMillis > 2_000) {
                throw new IllegalArgumentException("invalid combo step timing");
            }
        }
    }
}
