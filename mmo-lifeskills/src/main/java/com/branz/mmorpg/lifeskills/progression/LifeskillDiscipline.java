package com.branz.mmorpg.lifeskills.progression;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;

/** Stable identity for one visible lifeskill progression track. */
public record LifeskillDiscipline(DefinitionId id) {
    private static final String PREFIX = "lifeskill.";

    public LifeskillDiscipline {
        Objects.requireNonNull(id, "id");
        if (!id.value().startsWith(PREFIX) || id.value().length() == PREFIX.length()) {
            throw new IllegalArgumentException(
                    "lifeskill discipline ID must start with lifeskill.");
        }
    }

    public static LifeskillDiscipline of(String discipline) {
        Objects.requireNonNull(discipline, "discipline");
        if (discipline.isBlank()) {
            throw new IllegalArgumentException("discipline must not be blank");
        }
        return new LifeskillDiscipline(DefinitionId.of(PREFIX + discipline));
    }
}
