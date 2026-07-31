package com.branz.mmorpg.progression.build;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;
import java.util.Set;

/** Mutually exclusive build modifier with an authored mechanical tradeoff. */
public record FormDefinition(
        DefinitionId id,
        Set<String> families,
        String tradeoff,
        int attunementCost,
        double staminaCostMultiplier,
        double manaCostMultiplier,
        Set<String> tags,
        Set<String> conflictsWithTags) {
    public FormDefinition {
        Objects.requireNonNull(id, "id");
        families = Set.copyOf(Objects.requireNonNull(families, "families"));
        if (families.isEmpty()) {
            throw new IllegalArgumentException("families must contain at least one family");
        }
        tradeoff = requireText(tradeoff, "tradeoff");
        if (attunementCost < 0) {
            throw new IllegalArgumentException("attunementCost must not be negative");
        }
        if (!validMultiplier(staminaCostMultiplier) || !validMultiplier(manaCostMultiplier)) {
            throw new IllegalArgumentException(
                    "resource cost multipliers must be between 0.5 and 1.5");
        }
        if (staminaCostMultiplier == 1.0 && manaCostMultiplier == 1.0) {
            throw new IllegalArgumentException("a form must change at least one resource behavior");
        }
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
        conflictsWithTags =
                Set.copyOf(Objects.requireNonNull(conflictsWithTags, "conflictsWithTags"));
    }

    public boolean supports(String weaponFamily) {
        return families.contains("ANY") || families.contains(Objects.requireNonNull(weaponFamily));
    }

    private static boolean validMultiplier(double value) {
        return Double.isFinite(value) && value >= 0.5 && value <= 1.5;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
