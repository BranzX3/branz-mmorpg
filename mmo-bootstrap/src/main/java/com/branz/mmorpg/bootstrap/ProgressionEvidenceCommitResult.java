package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.persistence.progression.ProgressionEvidenceExecution;
import java.util.List;
import java.util.Objects;

record ProgressionEvidenceCommitResult(
        LoadedCharacterSession session, List<ProgressionEvidenceExecution> executions) {
    ProgressionEvidenceCommitResult {
        Objects.requireNonNull(session, "session");
        executions = List.copyOf(Objects.requireNonNull(executions, "executions"));
    }
}
