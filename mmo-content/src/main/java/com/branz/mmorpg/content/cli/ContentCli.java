package com.branz.mmorpg.content.cli;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.IdentifierErrorCode;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.catalog.ContentCatalog;
import com.branz.mmorpg.content.catalog.ContentCatalogEntry;
import com.branz.mmorpg.content.catalog.ContentCatalogServer;
import com.branz.mmorpg.content.catalog.ContentCatalogWriter;
import com.branz.mmorpg.content.diagnostic.ContentDiagnostic;
import com.branz.mmorpg.content.diagnostic.ValidationReport;
import com.branz.mmorpg.content.manifest.ContentManifest;
import com.branz.mmorpg.content.manifest.ContentManifestErrorCode;
import com.branz.mmorpg.content.manifest.ContentManifestParser;
import com.branz.mmorpg.content.reference.ContentReference;
import com.branz.mmorpg.content.report.ValidationReportWriter;
import com.branz.mmorpg.content.schema.JsonSchemaGenerator;
import com.branz.mmorpg.content.snapshot.ContentLoadFailure;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.content.snapshot.ContentSnapshotLoader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Content command shell backed by the same compiled snapshot model as runtime code. */
public final class ContentCli {
    private static final int USAGE_ERROR = 2;
    private static final int VALIDATION_ERROR = 3;

    private ContentCli() {}

    public static void main(String[] args) {
        System.exit(execute(args, System.out, System.err));
    }

    static int execute(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || "--help".equals(args[0]) || "help".equals(args[0])) {
            printHelp(out);
            return 0;
        }
        if ("validate".equals(args[0])) {
            if (args.length > 2) {
                err.println("Usage: mmo-content validate [path]");
                return USAGE_ERROR;
            }
            Path target = args.length == 2 ? Path.of(args[1]) : Path.of(".");
            return Files.isDirectory(target)
                    ? validateSnapshot(target, out, err)
                    : validateManifest(target, out, err);
        }
        if ("references".equals(args[0])) {
            if (args.length < 2 || args.length > 3) {
                err.println("Usage: mmo-content references <stable-id> [path]");
                return USAGE_ERROR;
            }
            Path target = args.length == 3 ? Path.of(args[2]) : Path.of(".");
            return printReferences(args[1], target, out, err);
        }
        if ("schema".equals(args[0])) {
            if (args.length > 2) {
                err.println("Usage: mmo-content schema [output-directory]");
                return USAGE_ERROR;
            }
            Path output = args.length == 2 ? Path.of(args[1]) : Path.of("schemas/generated");
            try {
                new JsonSchemaGenerator().write(output);
                out.println("Generated content schemas: " + output.toAbsolutePath().normalize());
                return 0;
            } catch (IOException exception) {
                err.println("CONTENT_SCHEMA_WRITE_FAILED: " + exception.getMessage());
                return VALIDATION_ERROR;
            }
        }
        if ("search".equals(args[0])) {
            if (args.length < 2 || args.length > 3) {
                err.println("Usage: mmo-content search <query> [path]");
                return USAGE_ERROR;
            }
            Path root = args.length == 3 ? Path.of(args[2]) : Path.of(".");
            Result<ContentSnapshot, ContentLoadFailure> loaded =
                    new ContentSnapshotLoader().load(root);
            if (!(loaded instanceof Result.Success<ContentSnapshot, ContentLoadFailure> success)) {
                printDiagnostics(
                        ((Result.Failure<ContentSnapshot, ContentLoadFailure>) loaded)
                                .error()
                                .diagnostics(),
                        err);
                return VALIDATION_ERROR;
            }
            List<ContentCatalogEntry> matches =
                    ContentCatalog.from(success.value()).search(args[1]);
            matches.forEach(
                    entry -> out.printf("%s\t%s\t%s%n", entry.id(), entry.type(), entry.source()));
            out.printf("%d result(s)%n", matches.size());
            return 0;
        }
        if ("catalog".equals(args[0])) {
            if (args.length > 3) {
                err.println("Usage: mmo-content catalog [path] [output-directory]");
                return USAGE_ERROR;
            }
            Path root = args.length >= 2 ? Path.of(args[1]) : Path.of(".");
            Path output = args.length == 3 ? Path.of(args[2]) : Path.of("build/content-catalog");
            Result<ContentSnapshot, ContentLoadFailure> loaded =
                    new ContentSnapshotLoader().load(root);
            if (!(loaded instanceof Result.Success<ContentSnapshot, ContentLoadFailure> success)) {
                printDiagnostics(
                        ((Result.Failure<ContentSnapshot, ContentLoadFailure>) loaded)
                                .error()
                                .diagnostics(),
                        err);
                return VALIDATION_ERROR;
            }
            try {
                new ContentCatalogWriter().write(ContentCatalog.from(success.value()), output);
                out.println("Wrote content catalog: " + output.toAbsolutePath().normalize());
                return 0;
            } catch (IOException exception) {
                err.println("CONTENT_CATALOG_WRITE_FAILED: " + exception.getMessage());
                return VALIDATION_ERROR;
            }
        }
        if ("serve-catalog".equals(args[0])) {
            if (args.length > 3) {
                err.println("Usage: mmo-content serve-catalog [path] [port]");
                return USAGE_ERROR;
            }
            Path root = args.length >= 2 ? Path.of(args[1]) : Path.of(".");
            int port;
            try {
                port = args.length == 3 ? Integer.parseInt(args[2]) : 8765;
            } catch (NumberFormatException exception) {
                err.println("Port must be an integer between 1 and 65535.");
                return USAGE_ERROR;
            }
            if (port < 1 || port > 65_535) {
                err.println("Port must be an integer between 1 and 65535.");
                return USAGE_ERROR;
            }
            return serveCatalog(root, port, out, err);
        }
        if ("report".equals(args[0])) {
            if (args.length > 3) {
                err.println("Usage: mmo-content report [path] [output-directory]");
                return USAGE_ERROR;
            }
            Path root = args.length >= 2 ? Path.of(args[1]) : Path.of(".");
            Path output = args.length == 3 ? Path.of(args[2]) : Path.of("build/content-report");
            Result<ContentSnapshot, ContentLoadFailure> loaded =
                    new ContentSnapshotLoader().load(root);
            ValidationReport report;
            if (loaded instanceof Result.Failure<ContentSnapshot, ContentLoadFailure> failure) {
                report = new ValidationReport(failure.error().diagnostics());
            } else {
                report = new ValidationReport(java.util.List.of());
            }
            try {
                new ValidationReportWriter().write(report, output);
                out.println("Wrote validation reports: " + output.toAbsolutePath().normalize());
                return report.hasErrors() ? VALIDATION_ERROR : 0;
            } catch (IOException exception) {
                err.println("CONTENT_REPORT_WRITE_FAILED: " + exception.getMessage());
                return VALIDATION_ERROR;
            }
        }

        err.println(
                "Command is not implemented in this milestone: "
                        + String.join(" ", Arrays.asList(args)));
        printHelp(err);
        return USAGE_ERROR;
    }

    private static int serveCatalog(Path root, int port, PrintStream out, PrintStream err) {
        Result<ContentSnapshot, ContentLoadFailure> loaded = new ContentSnapshotLoader().load(root);
        if (!(loaded instanceof Result.Success<ContentSnapshot, ContentLoadFailure> success)) {
            printDiagnostics(
                    ((Result.Failure<ContentSnapshot, ContentLoadFailure>) loaded)
                            .error()
                            .diagnostics(),
                    err);
            return VALIDATION_ERROR;
        }

        try (ContentCatalogServer server =
                ContentCatalogServer.start(ContentCatalog.from(success.value()), port)) {
            Thread shutdownHook = new Thread(server::close, "content-catalog-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            out.println("Serving read-only content catalog at " + server.baseUri());
            out.println("Snapshot is immutable; restart the command after content changes.");
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException exception) {
                    // JVM shutdown is already running the hook.
                }
            }
            return 0;
        } catch (IOException exception) {
            err.println("CONTENT_CATALOG_SERVER_FAILED: " + exception.getMessage());
            return VALIDATION_ERROR;
        }
    }

    private static int validateManifest(Path manifest, PrintStream out, PrintStream err) {
        Result<ContentManifest, ContentManifestErrorCode> result =
                new ContentManifestParser().parse(manifest);
        if (result instanceof Result.Success<ContentManifest, ContentManifestErrorCode> success) {
            ContentManifest value = success.value();
            out.printf(
                    "Valid content manifest: version=%s schema=%d definitions=%d%n",
                    value.contentVersion(), value.schemaVersion(), value.definitions().size());
            return 0;
        }
        Result.Failure<ContentManifest, ContentManifestErrorCode> failure =
                (Result.Failure<ContentManifest, ContentManifestErrorCode>) result;
        err.println(failure.error().code() + ": " + failure.detail());
        return VALIDATION_ERROR;
    }

    private static int validateSnapshot(Path root, PrintStream out, PrintStream err) {
        Result<ContentSnapshot, ContentLoadFailure> result = new ContentSnapshotLoader().load(root);
        if (result instanceof Result.Success<ContentSnapshot, ContentLoadFailure> success) {
            ContentSnapshot snapshot = success.value();
            out.printf(
                    "Valid content snapshot: version=%s definitions=%d references=%d%n",
                    snapshot.manifest().contentVersion(),
                    snapshot.definitions().size(),
                    snapshot.references().all().size());
            return 0;
        }
        printDiagnostics(
                ((Result.Failure<ContentSnapshot, ContentLoadFailure>) result)
                        .error()
                        .diagnostics(),
                err);
        return VALIDATION_ERROR;
    }

    private static int printReferences(String rawId, Path root, PrintStream out, PrintStream err) {
        Result<DefinitionId, IdentifierErrorCode> parsed = DefinitionId.parse(rawId);
        if (!(parsed instanceof Result.Success<DefinitionId, IdentifierErrorCode> id)) {
            Result.Failure<DefinitionId, IdentifierErrorCode> failure =
                    (Result.Failure<DefinitionId, IdentifierErrorCode>) parsed;
            err.println(failure.error().code() + ": " + failure.detail());
            return USAGE_ERROR;
        }
        Result<ContentSnapshot, ContentLoadFailure> loaded = new ContentSnapshotLoader().load(root);
        if (!(loaded instanceof Result.Success<ContentSnapshot, ContentLoadFailure> success)) {
            printDiagnostics(
                    ((Result.Failure<ContentSnapshot, ContentLoadFailure>) loaded)
                            .error()
                            .diagnostics(),
                    err);
            return VALIDATION_ERROR;
        }

        ContentSnapshot snapshot = success.value();
        if (snapshot.definitions().find(id.value()).isEmpty()) {
            err.println("CONTENT_REFERENCE_NOT_FOUND: " + id.value());
            return VALIDATION_ERROR;
        }
        out.println("Definition: " + id.value());
        printReferenceGroup("references", snapshot.references().outgoing(id.value()), out);
        printReferenceGroup("used by", snapshot.references().incoming(id.value()), out);
        return 0;
    }

    private static void printReferenceGroup(
            String heading, Iterable<ContentReference> references, PrintStream output) {
        output.println(heading + ":");
        boolean found = false;
        for (ContentReference reference : references) {
            found = true;
            DefinitionId id =
                    "references".equals(heading) ? reference.targetId() : reference.sourceId();
            output.printf("  %s (%s)%n", id, reference.sourceFile());
        }
        if (!found) {
            output.println("  (none)");
        }
    }

    private static void printDiagnostics(
            Iterable<ContentDiagnostic> diagnostics, PrintStream output) {
        for (ContentDiagnostic diagnostic : diagnostics) {
            output.printf(
                    "%s:%d:%d [%s] %s%n",
                    diagnostic.source(),
                    diagnostic.line(),
                    diagnostic.column(),
                    diagnostic.code().code(),
                    diagnostic.explanation());
        }
    }

    private static void printHelp(PrintStream output) {
        output.println(
                """
                mmo-content command shell

                Available now:
                  validate [path]                 Validate a manifest or content directory
                  references <stable-id> [path]   Show direct and reverse references
                  schema [output-directory]        Generate editor JSON Schemas
                  report [path] [output-directory] Write JSON and HTML validation reports
                  search <query> [path]             Search compiled catalog metadata
                  catalog [path] [output-directory] Export catalog and ID completions
                  serve-catalog [path] [port]        Serve a loopback-only read-only catalog API

                Reserved by the V1 contract:
                  build, diff, scaffold, simulate, test,
                  migrate, pack
                """);
    }
}
