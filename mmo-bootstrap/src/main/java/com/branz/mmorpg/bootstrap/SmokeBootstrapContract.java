package com.branz.mmorpg.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Repository-owned isolation and failure-evidence contract for deterministic bootstrap smoke runs.
 */
final class SmokeBootstrapContract {
    static final String ENABLED_PROPERTY = "mmo.bootstrap.smoke-test";
    static final String EMBEDDED_DATA_DIRECTORY_NAME = "smoke-embedded-postgres";
    static final String STARTUP_FAILURE_MARKER_NAME = "smoke-startup-failure.marker";

    private SmokeBootstrapContract() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLED_PROPERTY);
    }

    static Path embeddedDataDirectory(Path pluginDataDirectory, Path configuredDirectory) {
        Objects.requireNonNull(pluginDataDirectory, "pluginDataDirectory");
        Objects.requireNonNull(configuredDirectory, "configuredDirectory");
        if (!enabled()) {
            return configuredDirectory.toAbsolutePath().normalize();
        }
        return pluginDataDirectory
                .resolve(EMBEDDED_DATA_DIRECTORY_NAME)
                .toAbsolutePath()
                .normalize();
    }

    static Path startupFailureMarker(Path embeddedDataDirectory) {
        Objects.requireNonNull(embeddedDataDirectory, "embeddedDataDirectory");
        Path parent = embeddedDataDirectory.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("embeddedDataDirectory must have a parent");
        }
        return parent.resolve(STARTUP_FAILURE_MARKER_NAME);
    }

    static void recordStartupFailure(Path embeddedDataDirectory, Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        if (!enabled()) {
            return;
        }
        Path marker = startupFailureMarker(embeddedDataDirectory);
        String detail =
                failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, detail + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException markerFailure) {
            failure.addSuppressed(markerFailure);
        }
    }
}
