package com.branz.mmorpg.content.reference;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.IdentifierErrorCode;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.diagnostic.ContentDiagnostic;
import com.branz.mmorpg.content.diagnostic.ContentDiagnosticCode;
import com.branz.mmorpg.content.diagnostic.DiagnosticSeverity;
import com.branz.mmorpg.content.schema.DefinitionSchemas;
import com.branz.mmorpg.content.schema.ReferenceRule;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

public final class ReferenceExtractor {
    public ReferenceExtraction extract(ContentDefinition definition) {
        List<ContentReference> references = new ArrayList<>();
        List<ContentDiagnostic> diagnostics = new ArrayList<>();
        for (ReferenceRule rule : DefinitionSchemas.schema(definition.type()).referenceRules()) {
            collect(definition, definition.body(), rule, 0, references, diagnostics);
        }
        return new ReferenceExtraction(references, diagnostics);
    }

    private void collect(
            ContentDefinition definition,
            JsonNode current,
            ReferenceRule rule,
            int pathIndex,
            List<ContentReference> references,
            List<ContentDiagnostic> diagnostics) {
        if (current == null || current.isMissingNode() || current.isNull()) {
            return;
        }
        if (pathIndex == rule.path().size()) {
            if (current.isTextual()) {
                addReference(definition, current.textValue(), rule, references, diagnostics);
            }
            return;
        }
        String segment = rule.path().get(pathIndex);
        if ("*".equals(segment)) {
            if (current.isArray()) {
                current.forEach(
                        node ->
                                collect(
                                        definition,
                                        node,
                                        rule,
                                        pathIndex + 1,
                                        references,
                                        diagnostics));
            }
            return;
        }
        collect(definition, current.path(segment), rule, pathIndex + 1, references, diagnostics);
    }

    private void addReference(
            ContentDefinition definition,
            String target,
            ReferenceRule rule,
            List<ContentReference> references,
            List<ContentDiagnostic> diagnostics) {
        Result<DefinitionId, IdentifierErrorCode> parsed = DefinitionId.parse(target);
        if (!(parsed instanceof Result.Success<DefinitionId, IdentifierErrorCode> success)) {
            int[] location = findLocation(definition.sourceLines(), target);
            diagnostics.add(
                    new ContentDiagnostic(
                            ContentDiagnosticCode.CONTENT_ID_INVALID_FORMAT,
                            DiagnosticSeverity.ERROR,
                            definition.source(),
                            location[0],
                            location[1],
                            definition.id().value(),
                            "Reference is not a valid stable ID: " + target,
                            List.of(target),
                            "Use a lowercase dotted stable ID."));
            return;
        }
        int[] location = findLocation(definition.sourceLines(), target);
        references.add(
                new ContentReference(
                        definition.id(),
                        success.value(),
                        rule.expectedType(),
                        definition.source(),
                        location[0],
                        location[1]));
    }

    private static int[] findLocation(List<String> lines, String value) {
        for (int index = 0; index < lines.size(); index++) {
            int column = lines.get(index).indexOf(value);
            if (column >= 0) {
                return new int[] {index + 1, column + 1};
            }
        }
        return new int[] {1, 1};
    }
}
