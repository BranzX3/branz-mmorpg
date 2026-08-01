package com.branz.mmorpg.progression.knowledge;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;

/** Authored source identity and prerequisites for one permanent Knowledge grant. */
public record KnowledgeAcquisitionPolicy(
        KnowledgeKey target,
        KnowledgeAcquisitionSourceType sourceType,
        DefinitionId sourceId,
        LearningRequirements requirements) {
    public KnowledgeAcquisitionPolicy {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(requirements, "requirements");
    }
}
