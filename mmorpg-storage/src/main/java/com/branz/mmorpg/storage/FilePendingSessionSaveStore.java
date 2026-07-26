package com.branz.mmorpg.storage;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.lifeskill.LifeSkillProfile;
import com.branz.mmorpg.api.lifeskill.LifeSkillProgress;
import com.branz.mmorpg.api.lifeskill.LifeSkillSnapshot;
import com.branz.mmorpg.api.player.PendingSessionSave;
import com.branz.mmorpg.api.player.PendingSessionSaveStore;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.storage.player.FilePlayerProfileRecoveryStore;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Small per-player local journal used only when the authoritative database save
 * failed. A temporary file is fsynced by close and atomically renamed.
 */
public final class FilePendingSessionSaveStore implements PendingSessionSaveStore {

    private static final int MAGIC = 0x42524E5A;
    private static final int FORMAT = 2;
    private final Path directory;

    public FilePendingSessionSaveStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    @Override
    public Map<UUID, PendingSessionSave> loadAll() {
        ensureDirectory();
        Map<UUID, PendingSessionSave> loaded = new LinkedHashMap<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".pending"))
                    .sorted().toList()) {
                PendingSessionSave pending = read(file);
                loaded.put(pending.playerId(), pending);
            }
        } catch (IOException exception) {
            throw failure("failed to read pending-save journal " + directory, exception);
        }
        loadLegacyJsonRecords(loaded);
        return Map.copyOf(loaded);
    }

    @Override
    public void put(PendingSessionSave pending) {
        ensureDirectory();
        Path target = file(pending.playerId());
        Path temporary = directory.resolve(pending.playerId() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            write(output, pending);
        } catch (IOException exception) {
            throw failure("failed to write recovery snapshot for " + pending.playerId(), exception);
        }
        try {
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw failure("failed to commit recovery snapshot for " + pending.playerId(), exception);
        }
    }

    @Override
    public void remove(UUID playerId) {
        try {
            Files.deleteIfExists(file(playerId));
            Files.deleteIfExists(legacyFile(playerId));
        } catch (IOException exception) {
            throw failure("failed to remove recovered snapshot for " + playerId, exception);
        }
    }

    private PendingSessionSave read(Path file) {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("unsupported recovery file header");
            }
            int format = input.readInt();
            if (format < 1 || format > FORMAT) {
                throw new IOException("unsupported recovery file format " + format);
            }
            UUID playerId = new UUID(input.readLong(), input.readLong());
            String name = input.readUTF();
            int schema = input.readInt();
            Instant createdAt = Instant.ofEpochMilli(input.readLong());
            Instant lastSeenAt = Instant.ofEpochMilli(input.readLong());
            Optional<ContentId> classId = format >= 2 ? readContentId(input) : Optional.empty();
            Optional<ContentId> loadout = readContentId(input);
            Optional<ContentId> respawn = readContentId(input);
            Map<String, String> settings = readStrings(input);
            long revision = format >= 2 ? input.readLong() : 0;
            PlayerProfile profile = new PlayerProfile(playerId, name, schema, createdAt, lastSeenAt,
                    classId, loadout, respawn, settings, revision);

            int skillCount = checkedCount(input.readInt(), "skills");
            Map<ContentId, LifeSkillSnapshot> skills = new LinkedHashMap<>();
            for (int index = 0; index < skillCount; index++) {
                ContentId skillId = ContentId.parse(input.readUTF());
                LifeSkillProgress progress = new LifeSkillProgress(skillId, input.readInt(),
                        input.readLong(), input.readInt(), input.readLong(),
                        Instant.ofEpochMilli(input.readLong()));
                int rankCount = checkedCount(input.readInt(), "ranks");
                Map<ContentId, Integer> ranks = new HashMap<>();
                for (int rank = 0; rank < rankCount; rank++) {
                    ranks.put(ContentId.parse(input.readUTF()), input.readInt());
                }
                skills.put(skillId, new LifeSkillSnapshot(progress, ranks));
            }
            Instant loadedAt = Instant.ofEpochMilli(input.readLong());
            return new PendingSessionSave(profile, new LifeSkillProfile(playerId, skills, loadedAt));
        } catch (IOException | RuntimeException exception) {
            throw failure("invalid pending-save journal " + file, exception);
        }
    }

    private static void write(DataOutputStream output, PendingSessionSave pending) throws IOException {
        PlayerProfile profile = pending.profile();
        output.writeInt(MAGIC);
        output.writeInt(FORMAT);
        output.writeLong(profile.playerId().getMostSignificantBits());
        output.writeLong(profile.playerId().getLeastSignificantBits());
        output.writeUTF(profile.lastKnownName());
        output.writeInt(profile.schemaVersion());
        output.writeLong(profile.createdAt().toEpochMilli());
        output.writeLong(profile.lastSeenAt().toEpochMilli());
        writeContentId(output, profile.classId());
        writeContentId(output, profile.selectedLoadoutId());
        writeContentId(output, profile.respawnPointId());
        output.writeInt(profile.settings().size());
        for (Map.Entry<String, String> setting : profile.settings().entrySet()) {
            output.writeUTF(setting.getKey());
            output.writeUTF(setting.getValue());
        }
        output.writeLong(profile.revision());

        output.writeInt(pending.lifeSkills().skills().size());
        for (LifeSkillSnapshot snapshot : pending.lifeSkills().skills().values()) {
            LifeSkillProgress progress = snapshot.progress();
            output.writeUTF(progress.skillId().toString());
            output.writeInt(progress.level());
            output.writeLong(progress.totalXp());
            output.writeInt(progress.unspentPoints());
            output.writeLong(progress.treeRevision());
            output.writeLong(progress.updatedAt().toEpochMilli());
            output.writeInt(snapshot.nodeRanks().size());
            for (Map.Entry<ContentId, Integer> rank : snapshot.nodeRanks().entrySet()) {
                output.writeUTF(rank.getKey().toString());
                output.writeInt(rank.getValue());
            }
        }
        output.writeLong(pending.lifeSkills().loadedAt().toEpochMilli());
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw failure("failed to create pending-save directory " + directory, exception);
        }
    }

    private Path file(UUID playerId) {
        return directory.resolve(playerId + ".pending");
    }

    /** Imports recovery journals written by the pre-merge PlayerSessionManager. */
    private void loadLegacyJsonRecords(Map<UUID, PendingSessionSave> loaded) {
        FilePlayerProfileRecoveryStore legacy =
                new FilePlayerProfileRecoveryStore(directory, Runnable::run);
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().toList()) {
                String filename = file.getFileName().toString();
                UUID playerId = UUID.fromString(filename.substring(0, filename.length() - ".json".length()));
                if (loaded.containsKey(playerId)) {
                    continue;
                }
                legacy.load(playerId).toCompletableFuture().join().ifPresent(record ->
                        loaded.put(playerId, new PendingSessionSave(
                                record.profile(),
                                new LifeSkillProfile(playerId, Map.of(), record.recordedAt()))));
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw failure("failed to import legacy recovery journal " + directory, exception);
        }
    }

    private Path legacyFile(UUID playerId) {
        return directory.resolve(playerId + ".json");
    }

    private static Optional<ContentId> readContentId(DataInputStream input) throws IOException {
        return input.readBoolean() ? Optional.of(ContentId.parse(input.readUTF())) : Optional.empty();
    }

    private static void writeContentId(DataOutputStream output, Optional<ContentId> value)
            throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            output.writeUTF(value.get().toString());
        }
    }

    private static Map<String, String> readStrings(DataInputStream input) throws IOException {
        int count = checkedCount(input.readInt(), "settings");
        Map<String, String> values = new HashMap<>();
        for (int index = 0; index < count; index++) {
            values.put(input.readUTF(), input.readUTF());
        }
        return values;
    }

    private static int checkedCount(int count, String label) throws IOException {
        if (count < 0 || count > 100_000) {
            throw new IOException("invalid " + label + " count " + count);
        }
        return count;
    }

    private static MMOException failure(String message, Throwable cause) {
        return new MMOException(ErrorCode.STORAGE_FAILURE, message, cause);
    }
}
