package com.branz.mmorpg.progression.knowledge;

import java.util.Objects;
import java.util.Optional;

public record LearningDecision(
        boolean accepted, LearningRejectionReason reason, Optional<String> missingRequirement) {
    public LearningDecision {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(missingRequirement, "missingRequirement");
        if (accepted != (reason == LearningRejectionReason.NONE)) {
            throw new IllegalArgumentException("accepted decision must use NONE reason");
        }
        if (accepted && missingRequirement.isPresent()) {
            throw new IllegalArgumentException(
                    "accepted decision cannot have a missing requirement");
        }
    }

    public static LearningDecision acceptedDecision() {
        return new LearningDecision(true, LearningRejectionReason.NONE, Optional.empty());
    }

    public static LearningDecision rejected(
            LearningRejectionReason reason, String missingRequirement) {
        if (reason == LearningRejectionReason.NONE) {
            throw new IllegalArgumentException("rejection must have a reason");
        }
        return new LearningDecision(false, reason, Optional.of(missingRequirement));
    }
}
