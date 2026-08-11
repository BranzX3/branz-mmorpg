package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SmokeBootstrapContractTest {
    @TempDir Path tempDir;

    @Test
    void normalRuntimeKeepsConfiguredEmbeddedDirectory() {
        String previous = System.getProperty(SmokeBootstrapContract.ENABLED_PROPERTY);
        System.clearProperty(SmokeBootstrapContract.ENABLED_PROPERTY);
        try {
            Path configured = tempDir.resolve("embedded-postgres");
            assertEquals(
                    configured.toAbsolutePath().normalize(),
                    SmokeBootstrapContract.embeddedDataDirectory(tempDir, configured));
        } finally {
            restore(previous);
        }
    }

    @Test
    void smokeRuntimeUsesDedicatedEmbeddedDirectory() {
        String previous = System.getProperty(SmokeBootstrapContract.ENABLED_PROPERTY);
        System.setProperty(SmokeBootstrapContract.ENABLED_PROPERTY, "true");
        try {
            Path configured = tempDir.resolve("embedded-postgres");
            assertEquals(
                    tempDir
                            .resolve(SmokeBootstrapContract.EMBEDDED_DATA_DIRECTORY_NAME)
                            .toAbsolutePath()
                            .normalize(),
                    SmokeBootstrapContract.embeddedDataDirectory(tempDir, configured));
        } finally {
            restore(previous);
        }
    }

    @Test
    void smokeFailureWritesStableMarkerWithoutThrowing() throws Exception {
        String previous = System.getProperty(SmokeBootstrapContract.ENABLED_PROPERTY);
        System.setProperty(SmokeBootstrapContract.ENABLED_PROPERTY, "true");
        try {
            Path embedded = tempDir.resolve(SmokeBootstrapContract.EMBEDDED_DATA_DIRECTORY_NAME);
            IllegalStateException failure = new IllegalStateException("migration rejected");

            SmokeBootstrapContract.recordStartupFailure(embedded, failure);

            Path marker = SmokeBootstrapContract.startupFailureMarker(embedded);
            assertTrue(Files.isRegularFile(marker));
            assertEquals(
                    "IllegalStateException: migration rejected",
                    Files.readString(marker).trim());
            assertEquals(0, failure.getSuppressed().length);
        } finally {
            restore(previous);
        }
    }

    @Test
    void normalRuntimeDoesNotWriteSmokeFailureMarker() {
        String previous = System.getProperty(SmokeBootstrapContract.ENABLED_PROPERTY);
        System.clearProperty(SmokeBootstrapContract.ENABLED_PROPERTY);
        try {
            Path embedded = tempDir.resolve("embedded-postgres");
            SmokeBootstrapContract.recordStartupFailure(
                    embedded, new IllegalStateException("ignored outside smoke"));
            assertFalse(Files.exists(SmokeBootstrapContract.startupFailureMarker(embedded)));
        } finally {
            restore(previous);
        }
    }

    private static void restore(String previous) {
        if (previous == null) {
            System.clearProperty(SmokeBootstrapContract.ENABLED_PROPERTY);
        } else {
            System.setProperty(SmokeBootstrapContract.ENABLED_PROPERTY, previous);
        }
    }
}
