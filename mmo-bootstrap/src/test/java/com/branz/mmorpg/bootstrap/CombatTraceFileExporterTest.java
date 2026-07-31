package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.combat.action.ActionPhase;
import com.branz.mmorpg.combat.action.ActionTraceEvent;
import com.branz.mmorpg.combat.action.ActionTraceEventType;
import com.branz.mmorpg.combat.action.CombatResources;
import com.branz.mmorpg.combat.trace.CombatTrace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CombatTraceFileExporterTest {
    @TempDir Path temporaryDirectory;

    @Test
    void writesCanonicalTraceBeneathConfiguredDirectory() throws Exception {
        CombatTrace trace =
                new CombatTrace(
                        "content.test",
                        DefinitionId.of("move.test"),
                        CombatResources.full(1000, 100, 0),
                        List.of(),
                        List.of(
                                new ActionTraceEvent(
                                        0, ActionTraceEventType.ACTION_STARTED, "move.test")),
                        CombatResources.full(1000, 100, 0),
                        ActionPhase.COMPLETE);
        Path root = temporaryDirectory.resolve("combat-traces");

        Path exported = new CombatTraceFileExporter(root).export(UUID.randomUUID(), trace);

        assertTrue(exported.startsWith(root.toAbsolutePath()));
        assertTrue(exported.getFileName().toString().endsWith(".trace"));
        assertEquals(trace.canonicalExport(), Files.readString(exported, StandardCharsets.UTF_8));
    }
}
