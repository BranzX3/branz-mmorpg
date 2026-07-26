package com.branz.mmorpg.api.player;

import com.branz.mmorpg.api.content.ContentId;
<<<<<<< HEAD
import java.time.Instant;
import java.util.HashMap;
=======
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.time.Instant;
>>>>>>> 14f48819ebb179329fe30a79707d68429f4dc351
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

<<<<<<< HEAD
=======
/**
 * Persistent player profile, immutable.
 *
 * <p>{@code lastKnownName} is presentation metadata for admin output and never an
 * identity: the network runs offline-mode Velocity with FastLogin, so a name can
 * move between accounts and a premium player's UUID can change. Every lookup,
 * key, and join uses {@link #playerId}.
 *
 * @param playerId         identity
 * @param lastKnownName    most recent display name, for admin output only
 * @param schemaVersion    explicit profile schema version, for migration
 * @param createdAt        first login
 * @param lastSeenAt       most recent successful load or save
 * @param selectedLoadoutId active loadout, empty until one is chosen
 * @param respawnPointId   bound respawn point, empty until one is bound
 * @param settings         player-facing preferences
 */
>>>>>>> 14f48819ebb179329fe30a79707d68429f4dc351
public record PlayerProfile(
        UUID playerId,
        String lastKnownName,
        int schemaVersion,
        Instant createdAt,
        Instant lastSeenAt,
<<<<<<< HEAD
        Optional<ContentId> classId,
        Optional<ContentId> selectedLoadoutId,
        Optional<ContentId> respawnPointId,
        Map<String, String> settings,
        long revision) {

=======
        Optional<ContentId> selectedLoadoutId,
        Optional<ContentId> respawnPointId,
        Map<String, String> settings) {

    /** Schema version written by this build. */
>>>>>>> 14f48819ebb179329fe30a79707d68429f4dc351
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PlayerProfile {
        Objects.requireNonNull(playerId, "playerId");
<<<<<<< HEAD
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
=======
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        Objects.requireNonNull(selectedLoadoutId, "selectedLoadoutId");
        Objects.requireNonNull(respawnPointId, "respawnPointId");
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
        lastKnownName = lastKnownName == null ? "" : lastKnownName.trim();
        settings = Map.copyOf(settings);
    }

    /** Profile for a player logging in for the first time. */
    public static PlayerProfile createNew(UUID playerId, String name, Instant now) {
        return new PlayerProfile(playerId, name, CURRENT_SCHEMA_VERSION, now, now,
                Optional.empty(), Optional.empty(), Map.of());
    }

    public PlayerProfile withName(String name) {
        return new PlayerProfile(playerId, name, schemaVersion, createdAt, lastSeenAt,
                selectedLoadoutId, respawnPointId, settings);
    }

    public PlayerProfile withLastSeenAt(Instant at) {
        return new PlayerProfile(playerId, lastKnownName, schemaVersion, createdAt,
                Objects.requireNonNull(at, "at"), selectedLoadoutId, respawnPointId, settings);
    }

    public PlayerProfile withSelectedLoadout(ContentId loadoutId) {
        return new PlayerProfile(playerId, lastKnownName, schemaVersion, createdAt, lastSeenAt,
                Optional.ofNullable(loadoutId), respawnPointId, settings);
    }

    public PlayerProfile withRespawnPoint(ContentId respawnPointId) {
        return new PlayerProfile(playerId, lastKnownName, schemaVersion, createdAt, lastSeenAt,
                selectedLoadoutId, Optional.ofNullable(respawnPointId), settings);
>>>>>>> 14f48819ebb179329fe30a79707d68429f4dc351
    }

    public PlayerProfile withSetting(String key, String value) {
        Objects.requireNonNull(key, "key");
<<<<<<< HEAD
        Objects.requireNonNull(value, "value");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Setting key must not be blank");
        }
        Map<String, String> updated = new HashMap<>(settings);
        updated.put(key, value);
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
=======
        var updated = new java.util.HashMap<>(settings);
        if (value == null) {
            updated.remove(key);
        } else {
            updated.put(key, value);
        }
        return new PlayerProfile(playerId, lastKnownName, schemaVersion, createdAt, lastSeenAt,
                selectedLoadoutId, respawnPointId, updated);
    }

    public String setting(String key, String fallback) {
        return settings.getOrDefault(Objects.requireNonNull(key, "key"), fallback);
>>>>>>> 14f48819ebb179329fe30a79707d68429f4dc351
    }
}
