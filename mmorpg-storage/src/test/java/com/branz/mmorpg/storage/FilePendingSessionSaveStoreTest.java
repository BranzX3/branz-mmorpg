package com.branz.mmorpg.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.lifeskill.LifeSkillProfile;
import com.branz.mmorpg.api.lifeskill.LifeSkillProgress;
import com.branz.mmorpg.api.lifeskill.LifeSkillSnapshot;
import com.branz.mmorpg.api.player.PendingSessionSave;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.player.PlayerProfileRecoveryRecord;
import com.branz.mmorpg.storage.player.FilePlayerProfileRecoveryStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilePendingSessionSaveStoreTest {

    @TempDir
    Path temporary;

    @Test
    void roundTripsAndRemovesACompleteSessionSnapshot() {
        UUID playerId = UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
        Instant now = Instant.parse("2026-07-26T12:00:00Z");
        ContentId mining = ContentId.parse("branz:mining");
        ContentId node = ContentId.parse("branz:mining_stoneworker");
        PlayerProfile profile = new PlayerProfile(playerId, "Branz", 1, now.minusSeconds(60), now,
                Optional.of(ContentId.parse("branz:warrior")),
                Optional.of(ContentId.parse("branz:starter")), Optional.empty(),
                Map.of("hud", "compact"), 7);
        LifeSkillProgress progress = new LifeSkillProgress(mining, 5, 1_234L, 2, 9L, now);
        PendingSessionSave pending = new PendingSessionSave(profile,
                new LifeSkillProfile(playerId,
                        Map.of(mining, new LifeSkillSnapshot(progress, Map.of(node, 2))), now));
        FilePendingSessionSaveStore store = new FilePendingSessionSaveStore(temporary);

        store.put(pending);
        PendingSessionSave loaded = store.loadAll().get(playerId);

        assertEquals(profile, loaded.profile());
        assertEquals(1_234L, loaded.lifeSkills().skill(mining).totalXp());
        assertTrue(loaded.lifeSkills().hasNode(mining, node, 2));
        store.remove(playerId);
        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    void importsAndRemovesLegacyPlayerSessionJsonJournal() {
        UUID playerId = UUID.fromString("79dcc7c1-9c4e-42ba-985c-0955680a4cb4");
        Instant now = Instant.parse("2026-07-26T13:00:00Z");
        PlayerProfile profile = new PlayerProfile(
                playerId,
                "Legacy",
                1,
                now.minusSeconds(60),
                now,
                Optional.of(ContentId.parse("branz:rogue")),
                Optional.empty(),
                Optional.empty(),
                Map.of("language", "th_TH"),
                11);
        FilePlayerProfileRecoveryStore legacy =
                new FilePlayerProfileRecoveryStore(temporary, Runnable::run);
        legacy.write(new PlayerProfileRecoveryRecord(profile, now, "database offline"))
                .toCompletableFuture().join();

        FilePendingSessionSaveStore merged = new FilePendingSessionSaveStore(temporary);
        PendingSessionSave imported = merged.loadAll().get(playerId);

        assertEquals(profile, imported.profile());
        assertTrue(imported.lifeSkills().skills().isEmpty());
        merged.remove(playerId);
        assertTrue(Files.notExists(temporary.resolve(playerId + ".json")));
    }
}
