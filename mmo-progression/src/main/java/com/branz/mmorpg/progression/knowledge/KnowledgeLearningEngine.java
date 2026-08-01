package com.branz.mmorpg.progression.knowledge;

import com.branz.mmorpg.progression.evidence.ProgressionTrackType;
import com.branz.mmorpg.progression.evidence.ReadinessBand;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/** Deterministic prerequisite resolver shared by mentors, discoveries and player teaching. */
public final class KnowledgeLearningEngine {
    public LearningDecision evaluate(
            KnowledgeKey target, LearningRequirements requirements, KnowledgeProfile profile) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(profile, "profile");
        if (profile.knows(target)) {
            return LearningDecision.rejected(
                    LearningRejectionReason.ALREADY_KNOWN, target.id().value());
        }
        KnowledgeKey missingKnowledge =
                requirements.knowledge().stream()
                        .filter(required -> !profile.knows(required))
                        .sorted()
                        .findFirst()
                        .orElse(null);
        if (missingKnowledge != null) {
            return LearningDecision.rejected(
                    LearningRejectionReason.MISSING_KNOWLEDGE,
                    missingKnowledge.type() + ":" + missingKnowledge.id().value());
        }
        Map.Entry<com.branz.mmorpg.progression.evidence.ProgressionTrack, ReadinessBand>
                missingReadiness =
                        requirements.readiness().entrySet().stream()
                                .filter(
                                        required ->
                                                profile.readiness()
                                                                .getOrDefault(
                                                                        required.getKey(),
                                                                        ReadinessBand.UNFAMILIAR)
                                                                .ordinal()
                                                        < required.getValue().ordinal())
                                .sorted(
                                        Comparator.comparing(
                                                required -> required.getKey().id().value()))
                                .findFirst()
                                .orElse(null);
        if (missingReadiness != null) {
            LearningRejectionReason reason =
                    missingReadiness.getKey().type() == ProgressionTrackType.DISCIPLINE_MASTERY
                            ? LearningRejectionReason.MASTERY_NOT_READY
                            : LearningRejectionReason.CONDITIONING_NOT_READY;
            return LearningDecision.rejected(
                    reason,
                    missingReadiness.getKey().id().value() + ">=" + missingReadiness.getValue());
        }
        String missingFlag =
                requirements.worldFlags().stream()
                        .filter(flag -> !profile.worldFlags().contains(flag))
                        .sorted()
                        .findFirst()
                        .orElse(null);
        return missingFlag == null
                ? LearningDecision.acceptedDecision()
                : LearningDecision.rejected(
                        LearningRejectionReason.MISSING_WORLD_FLAG, missingFlag);
    }
}
