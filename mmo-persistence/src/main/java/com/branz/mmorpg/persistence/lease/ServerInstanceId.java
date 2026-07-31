package com.branz.mmorpg.persistence.lease;

public record ServerInstanceId(String value) {
    public ServerInstanceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("server instance ID is required");
        }
        if (value.length() > 128) {
            throw new IllegalArgumentException("server instance ID exceeds 128 characters");
        }
    }
}
