package com.branz.mmorpg.content.cli;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.manifest.ContentManifest;
import com.branz.mmorpg.content.manifest.ContentManifestErrorCode;
import com.branz.mmorpg.content.manifest.ContentManifestParser;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Milestone-0 command shell. Commands beyond manifest validation are reserved for later bounded
 * content-tool tasks.
 */
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
            Path manifest =
                    Files.isDirectory(target) ? target.resolve("content-manifest.json") : target;
            return validateManifest(manifest, out, err);
        }

        err.println(
                "Command is not implemented in this milestone: "
                        + String.join(" ", Arrays.asList(args)));
        printHelp(err);
        return USAGE_ERROR;
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

    private static void printHelp(PrintStream output) {
        output.println(
                """
                mmo-content command shell

                Available now:
                  validate [path]               Validate a content manifest

                Reserved by the V1 contract:
                  build, diff, scaffold, references, search, simulate, test,
                  migrate, pack, report, serve-catalog
                """);
    }
}
