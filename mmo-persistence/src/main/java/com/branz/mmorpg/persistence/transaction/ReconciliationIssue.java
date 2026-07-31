package com.branz.mmorpg.persistence.transaction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReconciliationIssue(
        ReconciliationIssueCode code,
        AuditSubjectType subjectType,
        UUID subjectId,
        String detail,
        Instant observedAt) {
    public ReconciliationIssue {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(subjectType, "subjectType");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(observedAt, "observedAt");
    }
}
