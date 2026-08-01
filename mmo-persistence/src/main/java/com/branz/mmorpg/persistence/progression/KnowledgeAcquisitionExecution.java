package com.branz.mmorpg.persistence.progression;

import java.util.Objects;

public record KnowledgeAcquisitionExecution(
        KnowledgeAcquisitionRecord acquisition,
        KnowledgeRecord learnedKnowledge,
        boolean replayed) {
    public KnowledgeAcquisitionExecution {
        Objects.requireNonNull(acquisition, "acquisition");
        Objects.requireNonNull(learnedKnowledge, "learnedKnowledge");
    }
}
