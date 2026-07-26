package com.branz.mmorpg.storage.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.player.PlayerProfileRecoveryRecord;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilePlayerProfileRecoveryStoreTest {
    private static final UUID PLAYER_ID = UUID.fromString("ac3356d5-3154-49ba-b9a9-b58af94c2903");
    private static final Instant NOW = Instant.parse("2026-07-26T08:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsAndDeletesARecoveryRecord() {
        FilePlayerProfileRecoveryStore store =
                new FilePlayerProfileRecoveryStore(temporaryDirectory, Runnable::run);
        PlayerProfile profile = new PlayerProfile(
                PLAYER_ID,
                "Tester",
                PlayerProfile.CURRENT_SCHEMA_VERSION,
                NOW.minusSeconds(60),
                NOW,
                Optional.of(ContentId.parse("branz:warrior")),
                Optional.of(ContentId.parse("branz:warrior/starter")),
                Optional.empty(),
                Map.of("hud", "compact"),
                7);
        PlayerProfileRecoveryRecord record =
                new PlayerProfileRecoveryRecord(profile, NOW, "database offline");

        store.write(record).toCompletableFuture().join();
        PlayerProfileRecoveryRecord loaded =
                store.load(PLAYER_ID).toCompletableFuture().join().orElseThrow();

        assertEquals(record, loaded);
        store.delete(PLAYER_ID).toCompletableFuture().join();
        assertTrue(store.load(PLAYER_ID).toCompletableFuture().join().isEmpty());
    }

    @Test
    void laterWriteAtomicallyReplacesThePreviousRecord() {
        FilePlayerProfileRecoveryStore store =
                new FilePlayerProfileRecoveryStore(temporaryDirectory, Runnable::run);
        PlayerProfile first = PlayerProfile.create(PLAYER_ID, "Tester", NOW);
        PlayerProfile second = first.withSetting("language", "th_TH");

        store.write(new PlayerProfileRecoveryRecord(first, NOW, "first"))
                .toCompletableFuture().join();
        store.write(new PlayerProfileRecoveryRecord(second, NOW.plusSeconds(1), "second"))
                .toCompletableFuture().join();

        PlayerProfileRecoveryRecord loaded =
                store.load(PLAYER_ID).toCompletableFuture().join().orElseThrow();
        assertEquals("second", loaded.failureDetail());
        assertEquals("th_TH", loaded.profile().settings().get("language"));
    }
}
