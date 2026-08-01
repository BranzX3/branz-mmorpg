package com.branz.mmorpg.progression.knowledge;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;

/** Resolves one server-authored acquisition source before permanent persistence. */
public final class KnowledgeAcquisitionEngine {
    private final KnowledgeLearningEngine learning;

    public KnowledgeAcquisitionEngine() {
        this(new KnowledgeLearningEngine());
    }

    public KnowledgeAcquisitionEngine(KnowledgeLearningEngine learning) {
        this.learning = Objects.requireNonNull(learning, "learning");
    }

    public LearningDecision evaluate(
            KnowledgeAcquisitionPolicy policy,
            KnowledgeAcquisitionSourceType observedSourceType,
            DefinitionId observedSourceId,
            KnowledgeProfile profile) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(observedSourceType, "observedSourceType");
        Objects.requireNonNull(observedSourceId, "observedSourceId");
        Objects.requireNonNull(profile, "profile");
        if (policy.sourceType() != observedSourceType
                || !policy.sourceId().equals(observedSourceId)) {
            return LearningDecision.rejected(
                    LearningRejectionReason.ACQUISITION_SOURCE_MISMATCH,
                    policy.sourceType() + ":" + policy.sourceId().value());
        }
        return learning.evaluate(policy.target(), policy.requirements(), profile);
    }
}
