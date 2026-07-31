package com.branz.mmorpg.content.report;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.content.diagnostic.ContentDiagnostic;
import com.branz.mmorpg.content.diagnostic.ContentDiagnosticCode;
import com.branz.mmorpg.content.diagnostic.DiagnosticSeverity;
import com.branz.mmorpg.content.diagnostic.ValidationReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationReportWriterTest {
    @TempDir Path outputDirectory;

    @Test
    void writesMachineReadableAndEscapedHtmlReports() throws IOException {
        ValidationReport report =
                new ValidationReport(
                        List.of(
                                new ContentDiagnostic(
                                        ContentDiagnosticCode.CONTENT_REFERENCE_NOT_FOUND,
                                        DiagnosticSeverity.ERROR,
                                        Path.of("nodes/test.yml"),
                                        4,
                                        11,
                                        "node.test",
                                        "Missing <material.test>",
                                        List.of("material.test"),
                                        "Add the item.")));

        new ValidationReportWriter().write(report, outputDirectory);

        String json = Files.readString(outputDirectory.resolve("validation-report.json"));
        String html = Files.readString(outputDirectory.resolve("validation-report.html"));
        assertTrue(json.contains("\"errorCount\" : 1"));
        assertTrue(json.contains("CONTENT_REFERENCE_NOT_FOUND"));
        assertTrue(html.contains("Missing &lt;material.test&gt;"));
        assertTrue(html.contains("Errors: 1"));
    }
}
