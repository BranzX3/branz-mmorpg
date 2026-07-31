package com.branz.mmorpg.content.validation;

import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.definition.DefinitionRegistry;
import com.branz.mmorpg.content.diagnostic.ContentDiagnostic;
import com.branz.mmorpg.content.diagnostic.ContentDiagnosticCode;
import com.branz.mmorpg.content.diagnostic.DiagnosticSeverity;
import com.branz.mmorpg.content.diagnostic.ValidationReport;
import com.branz.mmorpg.content.reference.ContentReference;
import com.branz.mmorpg.content.reference.ReferenceIndex;
import java.util.ArrayList;
import java.util.List;

public final class ContentValidator {
    private final FieldValidator fieldValidator = new FieldValidator();

    public ValidationReport validate(DefinitionRegistry registry, ReferenceIndex references) {
        List<ContentDiagnostic> diagnostics = new ArrayList<>();
        for (ContentDefinition definition : registry.all()) {
            diagnostics.addAll(fieldValidator.validate(definition));
        }
        for (ContentReference reference : references.all()) {
            ContentDefinition target = registry.find(reference.targetId()).orElse(null);
            if (target == null) {
                diagnostics.add(
                        diagnostic(
                                reference,
                                ContentDiagnosticCode.CONTENT_REFERENCE_NOT_FOUND,
                                "Referenced definition does not exist: " + reference.targetId(),
                                "Add the missing definition or update the reference."));
            } else if (target.type() != reference.expectedType()) {
                diagnostics.add(
                        diagnostic(
                                reference,
                                ContentDiagnosticCode.CONTENT_REFERENCE_WRONG_TYPE,
                                "Expected "
                                        + reference.expectedType()
                                        + " but found "
                                        + target.type()
                                        + ": "
                                        + reference.targetId(),
                                "Reference a definition of type "
                                        + reference.expectedType()
                                        + "."));
            }
        }
        return new ValidationReport(diagnostics);
    }

    private static ContentDiagnostic diagnostic(
            ContentReference reference,
            ContentDiagnosticCode code,
            String explanation,
            String repair) {
        return new ContentDiagnostic(
                code,
                DiagnosticSeverity.ERROR,
                reference.sourceFile(),
                reference.line(),
                reference.column(),
                reference.sourceId().value(),
                explanation,
                List.of(reference.targetId().value()),
                repair);
    }
}
