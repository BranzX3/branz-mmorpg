package com.branz.mmorpg.api.player;

import com.branz.mmorpg.api.content.ContentId;
<<<<<<< HEAD
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
=======
>>>>>>> parent of 3846639 (74)
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

<<<<<<< HEAD
/**
 * Immutable persistent player profile shared by the session and gameplay systems.
 * UUID is the identity; the last known name is presentation metadata only.
 */
=======
>>>>>>> parent of 3846639 (74)
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
<<<<<<< HEAD
=======
        Objects.requireNonNull(lastKnownName, "lastKnownName");
>>>>>>> parent of 3846639 (74)
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        classId = Objects.requireNonNull(classId, "classId");
        selectedLoadoutId = Objects.requireNonNull(selectedLoadoutId, "selectedLoadoutId");
        respawnPointId = Objects.requireNonNull(respawnPointId, "respawnPointId");
<<<<<<< HEAD
        Objects.requireNonNull(settings, "settings");
        if (schemaVersion < 1) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "schemaVersion must be at least 1: " + schemaVersion);
        }
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new MMOException(ErrorCode.STORAGE_FAILURE,
                    "profile schema " + schemaVersion + " is newer than this build supports ("
                            + CURRENT_SCHEMA_VERSION + "); refusing to load rather than truncate data");
        }
        if (revision < 0) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT, "revision must not be negative");
        }
        lastKnownName = lastKnownName == null ? "" : lastKnownName.trim();
        settings = Map.copyOf(settings);
    }

    /** Compatibility constructor for repositories created before class and revision were added. */
    public PlayerProfile(
            UUID playerId,
            String lastKnownName,
            int schemaVersion,
            Instant createdAt,
            Instant lastSeenAt,
            Optional<ContentId> selectedLoadoutId,
            Optional<ContentId> respawnPointId,
            Map<String, String> settings) {
        this(playerId, lastKnownName, schemaVersion, createdAt, lastSeenAt, Optional.empty(),
                selectedLoadoutId, respawnPointId, settings, 0);
    }

    public static PlayerProfile create(UUID playerId, String name, Instant now) {
        return new PlayerProfile(playerId, name, CURRENT_SCHEMA_VERSION, now, now,
                Optional.empty(), Optional.empty(), Optional.empty(), Map.of(), 0);
    }

    public static PlayerProfile createNew(UUID playerId, String name, Instant now) {
        return create(playerId, name, now);
    }

    public PlayerProfile seenAs(String name, Instant now) {
        return new PlayerProfile(playerId, name, schemaVersion, createdAt, now, classId,
                selectedLoadoutId, respawnPointId, settings, revision);
    }

    public PlayerProfile withName(String name) {
        return new PlayerProfile(playerId, name, schemaVersion, createdAt, lastSeenAt, classId,
                selectedLoadoutId, respawnPointId, settings, revision);
    }

    public PlayerProfile withLastSeenAt(Instant at) {
        return new PlayerProfile(playerId, lastKnownName, schemaVersion, createdAt,
                Objects.requireNonNull(at, "at"), classId, selectedLoadoutId, respawnPointId, settings, revision);
    }

    public PlayerProfile withRevision(long nextRevision) {
        return new PlayerProfile(playerId, lastKnownName, schemaVersion, createdAt, lastSeenAt, classId,
                selectedLoadoutId, respawnPointId, settings, nextRevision);
    }

    public PlayerProfile withClass(Optional<ContentId> nextClassId) {
        return new PlayerProfile(playerId, lastKnownName, schemaVersion, createdAt, lastSeenAt,
                Objects.requireNonNull(nextClassId, "nextClassId"), selectedLoadoutId, respawnPointId,
                settings, revision);
    }

    public PlayerProfile withSelectedLoadout(Optional<ContentId> nextLoadoutId) {
        return new PlayerProfile(playerId, lastKnownName, schemaVersion, createdAt, lastSeenAt, classId,
                Objects.requireNonNull(nextLoadoutId, "nextLoadoutId"), respawnPointId, settings, revision);
    }

    public PlayerProfile withSelectedLoadout(ContentId loadoutId) {
        return withSelectedLoadout(Optional.ofNullable(loadoutId));
    }

    public PlayerProfile withRespawnPoint(Optional<ContentId> nextRespawnPointId) {
        return new PlayerProfile(playerId, lastKnownName, schemaVersion, createdAt, lastSeenAt, classId,
                selectedLoadoutId, Objects.requireNonNull(nextRespawnPointId, "nextRespawnPointId"),
                settings, revision);
    }

    public PlayerProfile withRespawnPoint(ContentId respawnPointId) {
        return withRespawnPoint(Optional.ofNullable(respawnPointId));
=======
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

    public PlayerProfile withSelectedLoadout(Optional<ContentId> nextLoadoutId) {
        return new PlayerProfile(
                playerId,
                lastKnownName,
                schemaVersion,
                createdAt,
                lastSeenAt,
                classId,
                Objects.requireNonNull(nextLoadoutId, "nextLoadoutId"),
                respawnPointId,
                settings,
                revision);
    }

    public PlayerProfile withRespawnPoint(Optional<ContentId> nextRespawnPointId) {
        return new PlayerProfile(
                playerId,
                lastKnownName,
                schemaVersion,
                createdAt,
                lastSeenAt,
                classId,
                selectedLoadoutId,
                Objects.requireNonNull(nextRespawnPointId, "nextRespawnPointId"),
                settings,
                revision);
>>>>>>> parent of 3846639 (74)
    }

    public PlayerProfile withSetting(String key, String value) {
        Objects.requireNonNull(key, "key");
<<<<<<< HEAD
=======
        Objects.requireNonNull(value, "value");
>>>>>>> parent of 3846639 (74)
        if (key.isBlank()) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT, "setting key must not be blank");
        }
        Map<String, String> updated = new HashMap<>(settings);
        if (value == null) {
            updated.remove(key);
        } else {
            updated.put(key, value);
        }
        return withSettings(updated);
    }

    public PlayerProfile withoutSetting(String key) {
        Objects.requireNonNull(key, "key");
        if (!settings.containsKey(key)) {
            return this;
        }
        Map<String, String> updated = new HashMap<>(settings);
        updated.remove(key);
        return withSettings(updated);
    }

<<<<<<< HEAD
    public String setting(String key, String fallback) {
        return settings.getOrDefault(Objects.requireNonNull(key, "key"), fallback);
    }

    private PlayerProfile withSettings(Map<String, String> nextSettings) {
        return new PlayerProfile(playerId, lastKnownName, schemaVersion, createdAt, lastSeenAt, classId,
                selectedLoadoutId, respawnPointId, nextSettings, revision);
=======
    private PlayerProfile withSettings(Map<String, String> nextSettings) {
        return new PlayerProfile(
                playerId,
                lastKnownName,
                schemaVersion,
                createdAt,
                lastSeenAt,
                classId,
                selectedLoadoutId,
                respawnPointId,
                nextSettings,
                revision);
>>>>>>> parent of 3846639 (74)
    }
}
