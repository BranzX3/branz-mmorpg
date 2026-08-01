package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.persistence.progression.TeachingCommitExecution;
import java.util.Objects;

record TeachingSessionCommitResult(
        LoadedCharacterSession teacherSession,
        LoadedCharacterSession studentSession,
        TeachingCommitExecution execution) {
    TeachingSessionCommitResult {
        Objects.requireNonNull(teacherSession, "teacherSession");
        Objects.requireNonNull(studentSession, "studentSession");
        Objects.requireNonNull(execution, "execution");
    }
}
