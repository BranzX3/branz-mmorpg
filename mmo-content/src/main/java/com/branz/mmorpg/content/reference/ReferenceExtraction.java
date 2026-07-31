package com.branz.mmorpg.content.reference;

import com.branz.mmorpg.content.diagnostic.ContentDiagnostic;
import java.util.List;

public record ReferenceExtraction(
        List<ContentReference> references, List<ContentDiagnostic> diagnostics) {
    public ReferenceExtraction {
        references = List.copyOf(references);
        diagnostics = List.copyOf(diagnostics);
    }
}
