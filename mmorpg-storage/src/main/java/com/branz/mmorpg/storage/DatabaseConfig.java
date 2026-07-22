package com.branz.mmorpg.storage;

import java.util.Objects;

public record DatabaseConfig(
        String host,
        int port,
        String database,
        String username,
        String password,
        int maximumPoolSize,
        long connectionTimeoutMillis
) {
    public DatabaseConfig {
        host = requireText(host, "host");
        database = requireIdentifier(database, "database");
        username = requireText(username, "username");
        Objects.requireNonNull(password, "password");
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (maximumPoolSize < 1 || maximumPoolSize > 100) {
            throw new IllegalArgumentException("maximumPoolSize must be between 1 and 100");
        }
        if (connectionTimeoutMillis < 250) {
            throw new IllegalArgumentException("connectionTimeoutMillis must be at least 250");
        }
    }

    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ':' + port + '/' + database
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
                + "&useSSL=false&allowPublicKeyRetrieval=true";
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return trimmed;
    }

    private static String requireIdentifier(String value, String label) {
        String checked = requireText(value, label);
        if (!checked.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException(label + " may contain only letters, digits, and underscore");
        }
        return checked;
    }
}
