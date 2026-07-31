package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.combat.trace.CombatTrace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;

/** Writes canonical live traces only beneath the configured local trace directory. */
final class CombatTraceFileExporter {
    private final Path traceDirectory;

    CombatTraceFileExporter(Path traceDirectory) {
        this.traceDirectory =
                Objects.requireNonNull(traceDirectory, "traceDirectory")
                        .toAbsolutePath()
                        .normalize();
    }

    Path export(UUID combatantId, CombatTrace trace) throws IOException {
        Objects.requireNonNull(combatantId, "combatantId");
        Objects.requireNonNull(trace, "trace");
        Files.createDirectories(traceDirectory);
        Path destination =
                traceDirectory
                        .resolve(combatantId + "-" + UUID.randomUUID() + ".trace")
                        .normalize();
        if (!destination.startsWith(traceDirectory)) {
            throw new IOException("trace destination escaped its configured directory");
        }
        Files.writeString(
                destination,
                trace.canonicalExport(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        return destination.toAbsolutePath();
    }
}
