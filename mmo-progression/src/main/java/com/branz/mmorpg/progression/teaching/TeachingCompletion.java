package com.branz.mmorpg.progression.teaching;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import java.util.Objects;
import java.util.UUID;

/** Immutable idempotent intent consumed by the durable teaching transaction. */
public record TeachingCompletion(
        UUID teachingSessionId,
        CharacterId teacherId,
        CharacterId studentId,
        KnowledgeKey learnedTechnique) {
    public TeachingCompletion {
        Objects.requireNonNull(teachingSessionId, "teachingSessionId");
        Objects.requireNonNull(teacherId, "teacherId");
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(learnedTechnique, "learnedTechnique");
    }
}
