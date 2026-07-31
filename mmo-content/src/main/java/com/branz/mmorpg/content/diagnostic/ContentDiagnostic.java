package com.branz.mmorpg.content.diagnostic;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ContentDiagnostic(
        ContentDiagnosticCode code,
        DiagnosticSeverity severity,
        Path source,
        int line,
        int column,
        String definitionId,
        String explanation,
        List<String> relatedDefinitionIds,
        String suggestedRepair) {
    public ContentDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(source, "source");
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException("Source line and column must be positive");
        }
        definitionId = definitionId == null ? "" : definitionId;
        explanation = Objects.requireNonNull(explanation, "explanation");
        relatedDefinitionIds = List.copyOf(relatedDefinitionIds);
        suggestedRepair = Objects.requireNonNull(suggestedRepair, "suggestedRepair");
    }
}
