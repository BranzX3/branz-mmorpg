package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.persistence.progression.KnowledgeAcquisitionExecution;
import java.util.Objects;

record KnowledgeAcquisitionCommitResult(
        LoadedCharacterSession session, KnowledgeAcquisitionExecution execution) {
    KnowledgeAcquisitionCommitResult {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(execution, "execution");
    }
}
