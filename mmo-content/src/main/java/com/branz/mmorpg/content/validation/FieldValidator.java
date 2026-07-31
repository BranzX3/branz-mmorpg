package com.branz.mmorpg.content.validation;

import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.diagnostic.ContentDiagnostic;
import com.branz.mmorpg.content.diagnostic.ContentDiagnosticCode;
import com.branz.mmorpg.content.diagnostic.DiagnosticSeverity;
import com.branz.mmorpg.content.schema.DefinitionSchemas;
import com.branz.mmorpg.content.schema.FieldRule;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

public final class FieldValidator {
    public List<ContentDiagnostic> validate(ContentDefinition definition) {
        List<ContentDiagnostic> diagnostics = new ArrayList<>();
        for (FieldRule rule : DefinitionSchemas.schema(definition.type()).fieldRules()) {
            validatePath(definition, definition.body(), rule, 0, diagnostics);
        }
        return List.copyOf(diagnostics);
    }

    private void validatePath(
            ContentDefinition definition,
            JsonNode current,
            FieldRule rule,
            int pathIndex,
            List<ContentDiagnostic> diagnostics) {
        if (pathIndex == rule.path().size()) {
            validateValue(definition, current, rule, diagnostics);
            return;
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            if (rule.required()) {
                diagnostics.add(
                        diagnostic(
                                definition,
                                rule,
                                ContentDiagnosticCode.CONTENT_SCHEMA_REQUIRED_FIELD,
                                "Required field is missing: " + rule.displayPath()));
            }
            return;
        }
        String segment = rule.path().get(pathIndex);
        if ("*".equals(segment)) {
            if (current.isArray()) {
                current.forEach(
                        child -> validatePath(definition, child, rule, pathIndex + 1, diagnostics));
            } else if (current.isObject()) {
                current.valueStream()
                        .forEach(
                                child ->
                                        validatePath(
                                                definition,
                                                child,
                                                rule,
                                                pathIndex + 1,
                                                diagnostics));
            }
            return;
        }
        validatePath(definition, current.path(segment), rule, pathIndex + 1, diagnostics);
    }

    private void validateValue(
            ContentDefinition definition,
            JsonNode value,
            FieldRule rule,
            List<ContentDiagnostic> diagnostics) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            if (rule.required()) {
                diagnostics.add(
                        diagnostic(
                                definition,
                                rule,
                                ContentDiagnosticCode.CONTENT_SCHEMA_REQUIRED_FIELD,
                                "Required field is missing: " + rule.displayPath()));
            }
            return;
        }
        if (!rule.type().matches(value)) {
            diagnostics.add(
                    diagnostic(
                            definition,
                            rule,
                            ContentDiagnosticCode.CONTENT_SCHEMA_WRONG_TYPE,
                            "Expected " + rule.type() + " at " + rule.displayPath()));
            return;
        }
        if (value.isNumber()
                && ((rule.minimum() != null && value.doubleValue() < rule.minimum())
                        || (rule.maximum() != null && value.doubleValue() > rule.maximum()))) {
            diagnostics.add(
                    diagnostic(
                            definition,
                            rule,
                            ContentDiagnosticCode.CONTENT_SCHEMA_OUT_OF_RANGE,
                            "Value is outside the documented range at " + rule.displayPath()));
        }
        if (value.isArray()
                && ((rule.minItems() != null && value.size() < rule.minItems())
                        || (rule.maxItems() != null && value.size() > rule.maxItems()))) {
            diagnostics.add(
                    diagnostic(
                            definition,
                            rule,
                            ContentDiagnosticCode.CONTENT_SCHEMA_OUT_OF_RANGE,
                            "Array size is outside the documented range at " + rule.displayPath()));
        }
        if (!rule.allowedValues().isEmpty() && !rule.allowedValues().contains(value.asText())) {
            diagnostics.add(
                    diagnostic(
                            definition,
                            rule,
                            ContentDiagnosticCode.CONTENT_INVARIANT_VIOLATION,
                            "Unsupported value at "
                                    + rule.displayPath()
                                    + "; allowed: "
                                    + rule.allowedValues().stream().sorted().toList()));
        }
    }

    private static ContentDiagnostic diagnostic(
            ContentDefinition definition,
            FieldRule rule,
            ContentDiagnosticCode code,
            String explanation) {
        int[] location = findLocation(definition.sourceLines(), rule.path().getLast());
        return new ContentDiagnostic(
                code,
                DiagnosticSeverity.ERROR,
                definition.source(),
                location[0],
                location[1],
                definition.id().value(),
                explanation,
                List.of(),
                "Update the field to match the generated schema.");
    }

    private static int[] findLocation(List<String> lines, String field) {
        for (int index = 0; index < lines.size(); index++) {
            int column = lines.get(index).indexOf(field);
            if (column >= 0) {
                return new int[] {index + 1, column + 1};
            }
        }
        return new int[] {1, 1};
    }
}
