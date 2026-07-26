package com.branz.mmorpg.api.player;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PlayerProfile(
        UUID playerId,
        String lastKnownName,
        int schemaVersion,
        Instant createdAt,
        Instant lastSeenAt,
        Optional<ContentId> classId,
        Optional<ContentId> selectedLoadoutId,
        Optional<ContentId> respawnPointId,
        Map<String, String> settings,
        long revision) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PlayerProfile {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lastKnownName, "lastKnownName");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        classId = Objects.requireNonNull(classId, "classId");
        selectedLoadoutId = Objects.requireNonNull(selectedLoadoutId, "selectedLoadoutId");
        respawnPointId = Objects.requireNonNull(respawnPointId, "respawnPointId");
        settings = Map.copyOf(settings);
        if (lastKnownName.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("Schema version must be positive");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("Revision must not be negative");
        }
    }

    public static PlayerProfile create(UUID playerId, String name, Instant now) {
        return new PlayerProfile(
                playerId,
                name,
                CURRENT_SCHEMA_VERSION,
                now,
                now,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                0);
    }

    public PlayerProfile seenAs(String name, Instant now) {
        return new PlayerProfile(
                playerId,
                name,
                schemaVersion,
                createdAt,
                now,
                classId,
                selectedLoadoutId,
                respawnPointId,
                settings,
                revision);
    }

    public PlayerProfile withRevision(long nextRevision) {
        return new PlayerProfile(
                playerId,
                lastKnownName,
                schemaVersion,
                createdAt,
                lastSeenAt,
                classId,
                selectedLoadoutId,
                respawnPointId,
                settings,
                nextRevision);
    }
}
