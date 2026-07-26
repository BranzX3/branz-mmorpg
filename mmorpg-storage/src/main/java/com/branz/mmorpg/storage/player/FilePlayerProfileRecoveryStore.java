package com.branz.mmorpg.storage.player;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.player.PlayerProfileRecoveryRecord;
import com.branz.mmorpg.api.player.PlayerProfileRecoveryStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/** Atomic, one-file-per-player recovery journal independent of MySQL availability. */
public final class FilePlayerProfileRecoveryStore implements PlayerProfileRecoveryStore {
    private final Path directory;
    private final Executor executor;
    private final ObjectMapper objectMapper;

    public FilePlayerProfileRecoveryStore(Path directory, Executor executor) {
        this(directory, executor, new ObjectMapper());
    }

    FilePlayerProfileRecoveryStore(Path directory, Executor executor, ObjectMapper objectMapper) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.executor = Objects.requireNonNull(executor, "executor");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public CompletionStage<Optional<PlayerProfileRecoveryRecord>> load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return runAsync(() -> {
            Path path = pathFor(playerId);
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            return Optional.of(decode(objectMapper.readTree(path.toFile())));
        });
    }

    @Override
    public CompletionStage<Void> write(PlayerProfileRecoveryRecord record) {
        Objects.requireNonNull(record, "record");
        return runAsync(() -> {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, record.profile().playerId() + ".", ".tmp");
            try {
                objectMapper.writeValue(temporary.toFile(), encode(record));
                moveAtomically(temporary, pathFor(record.profile().playerId()));
            } finally {
                Files.deleteIfExists(temporary);
            }
            return null;
        });
    }

    @Override
    public CompletionStage<Void> delete(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return runAsync(() -> {
            Files.deleteIfExists(pathFor(playerId));
            return null;
        });
    }

    private ObjectNode encode(PlayerProfileRecoveryRecord record) {
        PlayerProfile profile = record.profile();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("recordedAt", record.recordedAt().toString());
        root.put("failureDetail", record.failureDetail());
        root.put("playerId", profile.playerId().toString());
        root.put("lastKnownName", profile.lastKnownName());
        root.put("schemaVersion", profile.schemaVersion());
        root.put("createdAt", profile.createdAt().toString());
        root.put("lastSeenAt", profile.lastSeenAt().toString());
        putOptional(root, "classId", profile.classId());
        putOptional(root, "selectedLoadoutId", profile.selectedLoadoutId());
        putOptional(root, "respawnPointId", profile.respawnPointId());
        ObjectNode settings = root.putObject("settings");
        profile.settings().forEach(settings::put);
        root.put("revision", profile.revision());
        return root;
    }

    private PlayerProfileRecoveryRecord decode(JsonNode root) throws IOException {
        try {
            Map<String, String> settings = new HashMap<>();
            JsonNode settingsNode = required(root, "settings");
            if (!settingsNode.isObject()) {
                throw new IOException("Recovery field is not an object: settings");
            }
            settingsNode.properties().forEach(entry -> settings.put(entry.getKey(), entry.getValue().asText()));
            PlayerProfile profile = new PlayerProfile(
                    UUID.fromString(text(root, "playerId")),
                    text(root, "lastKnownName"),
                    integer(root, "schemaVersion"),
                    Instant.parse(text(root, "createdAt")),
                    Instant.parse(text(root, "lastSeenAt")),
                    contentId(root, "classId"),
                    contentId(root, "selectedLoadoutId"),
                    contentId(root, "respawnPointId"),
                    settings,
                    number(root, "revision"));
            return new PlayerProfileRecoveryRecord(
                    profile,
                    Instant.parse(text(root, "recordedAt")),
                    text(root, "failureDetail"));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid player recovery record", exception);
        }
    }

    private Path pathFor(UUID playerId) {
        return directory.resolve(playerId + ".json");
    }

    private <T> CompletionStage<T> runAsync(IoSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void putOptional(ObjectNode root, String field, Optional<ContentId> value) {
        if (value.isPresent()) {
            root.put(field, value.orElseThrow().toString());
        } else {
            root.putNull(field);
        }
    }

    private static Optional<ContentId> contentId(JsonNode root, String field) throws IOException {
        JsonNode value = required(root, field);
        return value.isNull() ? Optional.empty() : Optional.of(ContentId.parse(value.asText()));
    }

    private static String text(JsonNode root, String field) throws IOException {
        JsonNode value = required(root, field);
        if (!value.isTextual()) {
            throw new IOException("Recovery field is not text: " + field);
        }
        return value.asText();
    }

    private static int integer(JsonNode root, String field) throws IOException {
        JsonNode value = required(root, field);
        if (!value.canConvertToInt()) {
            throw new IOException("Recovery field is not an integer: " + field);
        }
        return value.intValue();
    }

    private static long number(JsonNode root, String field) throws IOException {
        JsonNode value = required(root, field);
        if (!value.canConvertToLong()) {
            throw new IOException("Recovery field is not a long: " + field);
        }
        return value.longValue();
    }

    private static JsonNode required(JsonNode root, String field) throws IOException {
        JsonNode value = root.get(field);
        if (value == null) {
            throw new IOException("Missing recovery field: " + field);
        }
        return value;
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
