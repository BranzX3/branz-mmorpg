package com.branz.mmorpg.persistence.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record SqlMigration(int version, String description, String sql, String checksum) {
    public SqlMigration {
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        description = requireText(description, "description");
        sql = requireText(sql, "sql");
        checksum = requireText(checksum, "checksum");
    }

    public static SqlMigration of(int version, String description, String sql) {
        return new SqlMigration(version, description, sql, sha256(sql));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
