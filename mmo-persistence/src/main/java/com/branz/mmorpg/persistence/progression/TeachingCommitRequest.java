package com.branz.mmorpg.persistence.progression;

import com.branz.mmorpg.progression.knowledge.KnowledgeType;
import com.branz.mmorpg.progression.renown.RenownDeedCandidate;
import com.branz.mmorpg.progression.teaching.TeachingCompletion;
import java.util.Objects;

public record TeachingCommitRequest(
        TeachingCompletion completion, RenownDeedCandidate teacherReward) {
    public TeachingCommitRequest {
        Objects.requireNonNull(completion, "completion");
        Objects.requireNonNull(teacherReward, "teacherReward");
        if (completion.learnedTechnique().type() != KnowledgeType.TECHNIQUE) {
            throw new IllegalArgumentException(
                    "player teaching may persist Technique knowledge only");
        }
        if (!completion.teacherId().equals(teacherReward.characterId())) {
            throw new IllegalArgumentException("teacher reward must belong to the teacher");
        }
        if (!teacherReward.deedType().value().equals("renown.mentorship")) {
            throw new IllegalArgumentException("teacher reward must use renown.mentorship");
        }
    }
}
