package com.branz.mmorpg.content.report;

import com.branz.mmorpg.content.diagnostic.ContentDiagnostic;
import com.branz.mmorpg.content.diagnostic.DiagnosticSeverity;
import com.branz.mmorpg.content.diagnostic.ValidationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ValidationReportWriter {
    private final ObjectMapper mapper = new ObjectMapper();

    public void write(ValidationReport report, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(
                        outputDirectory.resolve("validation-report.json").toFile(), toJson(report));
        Files.writeString(
                outputDirectory.resolve("validation-report.html"),
                toHtml(report),
                StandardCharsets.UTF_8);
    }

    private ObjectNode toJson(ValidationReport report) {
        ObjectNode root = mapper.createObjectNode();
        root.put("errorCount", count(report, DiagnosticSeverity.ERROR));
        root.put("warningCount", count(report, DiagnosticSeverity.WARNING));
        ArrayNode diagnostics = root.putArray("diagnostics");
        for (ContentDiagnostic diagnostic : report.diagnostics()) {
            ObjectNode item = diagnostics.addObject();
            item.put("code", diagnostic.code().code());
            item.put("severity", diagnostic.severity().name());
            item.put("file", diagnostic.source().toString().replace('\\', '/'));
            item.put("line", diagnostic.line());
            item.put("column", diagnostic.column());
            item.put("definitionId", diagnostic.definitionId());
            item.put("explanation", diagnostic.explanation());
            item.put("suggestedRepair", diagnostic.suggestedRepair());
            ArrayNode related = item.putArray("relatedDefinitionIds");
            diagnostic.relatedDefinitionIds().forEach(related::add);
        }
        return root;
    }

    private String toHtml(ValidationReport report) {
        StringBuilder rows = new StringBuilder();
        for (ContentDiagnostic diagnostic : report.diagnostics()) {
            rows.append("<tr><td>")
                    .append(escape(diagnostic.severity().name()))
                    .append("</td><td>")
                    .append(escape(diagnostic.code().code()))
                    .append("</td><td>")
                    .append(escape(diagnostic.source().toString()))
                    .append(':')
                    .append(diagnostic.line())
                    .append(':')
                    .append(diagnostic.column())
                    .append("</td><td>")
                    .append(escape(diagnostic.definitionId()))
                    .append("</td><td>")
                    .append(escape(diagnostic.explanation()))
                    .append("</td><td>")
                    .append(escape(diagnostic.suggestedRepair()))
                    .append("</td></tr>");
        }
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>MMO Content Validation</title>
                  <style>
                    body{font-family:system-ui,sans-serif;margin:2rem;color:#17212b}
                    table{border-collapse:collapse;width:100%%}
                    th,td{border:1px solid #ccd5df;padding:.5rem;text-align:left;vertical-align:top}
                    th{background:#eef3f8}
                  </style>
                </head>
                <body>
                  <h1>MMO Content Validation</h1>
                  <p>Errors: %d · Warnings: %d</p>
                  <table>
                    <thead><tr><th>Severity</th><th>Code</th><th>Source</th><th>Definition</th><th>Explanation</th><th>Repair</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                </body>
                </html>
                """
                .formatted(
                        count(report, DiagnosticSeverity.ERROR),
                        count(report, DiagnosticSeverity.WARNING),
                        rows);
    }

    private static long count(ValidationReport report, DiagnosticSeverity severity) {
        return report.diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == severity)
                .count();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
