package com.branz.mmorpg.persistence.progression;

import java.util.Objects;
import java.util.Optional;

public record TeachingCommitExecution(
        TeachingCompletionRecord teaching,
        KnowledgeRecord learnedKnowledge,
        RenownDeedRecord teacherDeed,
        Optional<RenownRecord> teacherRenown,
        boolean replayed) {
    public TeachingCommitExecution {
        Objects.requireNonNull(teaching, "teaching");
        Objects.requireNonNull(learnedKnowledge, "learnedKnowledge");
        Objects.requireNonNull(teacherDeed, "teacherDeed");
        Objects.requireNonNull(teacherRenown, "teacherRenown");
    }
}
