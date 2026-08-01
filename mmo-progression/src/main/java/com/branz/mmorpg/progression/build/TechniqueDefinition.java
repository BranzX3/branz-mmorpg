package com.branz.mmorpg.progression.build;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.progression.evidence.ProgressionTrack;
import com.branz.mmorpg.progression.evidence.ReadinessBand;
import java.util.Objects;
import java.util.Set;

public record TechniqueDefinition(
        DefinitionId id,
        String family,
        MovesetBranch branch,
        DefinitionId moveId,
        TechniqueMode mode,
        String masteryDiscipline,
        boolean supernatural,
        int attunementCost,
        ReadinessBand learningReadiness,
        ReadinessBand teachingReadiness,
        Set<String> tags,
        Set<String> conflictsWithTags) {
    public TechniqueDefinition {
        Objects.requireNonNull(id, "id");
        family = requireText(family, "family");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(moveId, "moveId");
        Objects.requireNonNull(mode, "mode");
        masteryDiscipline = requireText(masteryDiscipline, "masteryDiscipline");
        ProgressionTrack.mastery(masteryDiscipline);
        if (attunementCost < 0 || (!supernatural && attunementCost != 0)) {
            throw new IllegalArgumentException(
                    "attunementCost must be non-negative and zero for mundane techniques");
        }
        Objects.requireNonNull(learningReadiness, "learningReadiness");
        Objects.requireNonNull(teachingReadiness, "teachingReadiness");
        if (teachingReadiness.ordinal() < learningReadiness.ordinal()) {
            throw new IllegalArgumentException(
                    "teachingReadiness must not be below learningReadiness");
        }
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
        conflictsWithTags =
                Set.copyOf(Objects.requireNonNull(conflictsWithTags, "conflictsWithTags"));
    }

    public boolean supports(String weaponFamily) {
        return family.equals("ANY") || family.equals(Objects.requireNonNull(weaponFamily));
    }

    public ProgressionTrack masteryTrack() {
        return ProgressionTrack.mastery(masteryDiscipline);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
