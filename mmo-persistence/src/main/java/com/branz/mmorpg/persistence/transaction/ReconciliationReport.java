package com.branz.mmorpg.persistence.transaction;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ReconciliationReport(Instant scannedAt, List<ReconciliationIssue> issues) {
    public ReconciliationReport {
        Objects.requireNonNull(scannedAt, "scannedAt");
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public boolean healthy() {
        return issues.isEmpty();
    }
}
