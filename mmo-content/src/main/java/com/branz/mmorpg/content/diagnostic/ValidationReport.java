package com.branz.mmorpg.content.diagnostic;

import java.util.List;

public record ValidationReport(List<ContentDiagnostic> diagnostics) {
    public ValidationReport {
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    }
}
