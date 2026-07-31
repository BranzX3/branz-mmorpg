package com.branz.mmorpg.bootstrap;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;

record DatabaseSettings(
        DatabaseMode mode,
        String environment,
        Path embeddedDataDirectory,
        String jdbcUrl,
        String username,
        String password,
        int maximumPoolSize,
        Duration connectionTimeout,
        boolean runMigrations,
        Duration leaseTtl,
        Duration leaseHeartbeat) {
    DatabaseSettings {
        Objects.requireNonNull(mode, "mode");
        environment = requireText(environment, "environment");
        Objects.requireNonNull(embeddedDataDirectory, "embeddedDataDirectory");
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        if (maximumPoolSize < 1 || maximumPoolSize > 64) {
            throw new IllegalArgumentException("maximumPoolSize must be between 1 and 64");
        }
        Objects.requireNonNull(connectionTimeout, "connectionTimeout");
        Objects.requireNonNull(leaseTtl, "leaseTtl");
        Objects.requireNonNull(leaseHeartbeat, "leaseHeartbeat");
        if (connectionTimeout.isNegative() || connectionTimeout.isZero()) {
            throw new IllegalArgumentException("connectionTimeout must be positive");
        }
        if (leaseTtl.isNegative() || leaseTtl.isZero()) {
            throw new IllegalArgumentException("leaseTtl must be positive");
        }
        if (leaseHeartbeat.isNegative() || leaseHeartbeat.isZero()) {
            throw new IllegalArgumentException("leaseHeartbeat must be positive");
        }
        if (leaseHeartbeat.compareTo(leaseTtl) >= 0) {
            throw new IllegalArgumentException("leaseHeartbeat must be shorter than leaseTtl");
        }
        if (mode == DatabaseMode.EXTERNAL && jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("external database jdbcUrl is required");
        }
        if (mode == DatabaseMode.EMBEDDED_LOCAL
                && !environment.equals("LOCAL")
                && !environment.equals("INTEGRATION")) {
            throw new IllegalArgumentException(
                    "embedded PostgreSQL is restricted to LOCAL or INTEGRATION");
        }
    }

    static DatabaseSettings from(FileConfiguration config, Path pluginDataDirectory) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(pluginDataDirectory, "pluginDataDirectory");
        String environment =
                config.getString("environment", "LOCAL").trim().toUpperCase(Locale.ROOT);
        DatabaseMode mode =
                DatabaseMode.valueOf(
                        config.getString("database.mode", "EMBEDDED_LOCAL")
                                .trim()
                                .toUpperCase(Locale.ROOT));
        return new DatabaseSettings(
                mode,
                environment,
                pluginDataDirectory
                        .resolve(
                                config.getString(
                                        "database.embedded-data-directory", "embedded-postgres"))
                        .toAbsolutePath()
                        .normalize(),
                config.getString("database.jdbc-url", "").trim(),
                config.getString("database.username", "").trim(),
                config.getString("database.password", ""),
                config.getInt("database.maximum-pool-size", 8),
                Duration.ofMillis(config.getLong("database.connection-timeout-millis", 5000)),
                config.getBoolean("database.run-migrations", true),
                Duration.ofSeconds(config.getLong("database.lease-ttl-seconds", 30)),
                Duration.ofSeconds(config.getLong("database.lease-heartbeat-seconds", 10)));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
