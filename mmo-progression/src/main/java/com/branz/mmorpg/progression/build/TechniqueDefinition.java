package com.branz.mmorpg.progression.build;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;
import java.util.Set;

public record TechniqueDefinition(
        DefinitionId id,
        String family,
        MovesetBranch branch,
        DefinitionId moveId,
        TechniqueMode mode,
        boolean supernatural,
        int attunementCost,
        Set<String> tags,
        Set<String> conflictsWithTags) {
    public TechniqueDefinition {
        Objects.requireNonNull(id, "id");
        family = requireText(family, "family");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(moveId, "moveId");
        Objects.requireNonNull(mode, "mode");
        if (attunementCost < 0 || (!supernatural && attunementCost != 0)) {
            throw new IllegalArgumentException(
                    "attunementCost must be non-negative and zero for mundane techniques");
        }
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
        conflictsWithTags =
                Set.copyOf(Objects.requireNonNull(conflictsWithTags, "conflictsWithTags"));
    }

    public boolean supports(String weaponFamily) {
        return family.equals("ANY") || family.equals(Objects.requireNonNull(weaponFamily));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
