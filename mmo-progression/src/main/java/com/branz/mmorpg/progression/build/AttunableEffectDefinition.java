package com.branz.mmorpg.progression.build;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.progression.knowledge.KnowledgeAcquisitionPolicy;
import java.util.Objects;
import java.util.Set;

public record AttunableEffectDefinition(
        DefinitionId id,
        int attunementCost,
        Set<String> tags,
        Set<String> conflictsWithTags,
        KnowledgeAcquisitionPolicy acquisition) {
    public AttunableEffectDefinition {
        Objects.requireNonNull(id, "id");
        if (attunementCost < 0) {
            throw new IllegalArgumentException("attunementCost must not be negative");
        }
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
        conflictsWithTags =
                Set.copyOf(Objects.requireNonNull(conflictsWithTags, "conflictsWithTags"));
        acquisition = Objects.requireNonNull(acquisition, "acquisition");
        if (!acquisition.target().id().equals(id)) {
            throw new IllegalArgumentException(
                    "attunement acquisition target must match effect ID");
        }
    }
}
