package com.branz.mmorpg.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.player.PlayerProfileStore;
import com.branz.mmorpg.api.player.PlayerSessionSnapshot;
import com.branz.mmorpg.api.player.PlayerSessionState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class PlayerSessionManagerTest {
    private static final Instant NOW = Instant.parse("2026-07-26T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID PLAYER_ID = UUID.fromString("ac3356d5-3154-49ba-b9a9-b58af94c2903");

    @Test
    void opensAndClosesAnAuthoritativeSession() {
        FakeStore store = new FakeStore();
        PlayerSessionManager sessions = new PlayerSessionManager(store, CLOCK);

        PlayerSessionSnapshot active = sessions.open(PLAYER_ID, "Tester", 7).toCompletableFuture().join();
        PlayerSessionSnapshot closed = sessions.close(PLAYER_ID, active.token()).toCompletableFuture().join();

        assertEquals(PlayerSessionState.ACTIVE, active.state());
        assertTrue(active.profile().isPresent());
        assertEquals(PlayerSessionState.CLOSED, closed.state());
        assertTrue(sessions.snapshot(PLAYER_ID).isEmpty());
        assertEquals(1, store.saveCount);
    }

    @Test
    void duplicateOpenIsRejectedWithoutReplacingActiveSession() {
        PlayerSessionManager sessions = new PlayerSessionManager(new FakeStore(), CLOCK);
        PlayerSessionSnapshot active = sessions.open(PLAYER_ID, "Tester", 1).toCompletableFuture().join();

        PlayerSessionSnapshot duplicate = sessions.open(PLAYER_ID, "Tester", 1).toCompletableFuture().join();

        assertEquals(PlayerSessionState.CONFLICTED, duplicate.state());
        assertEquals(active.token(), sessions.snapshot(PLAYER_ID).orElseThrow().token());
    }

    @Test
    void loadFailureFailsClosedWithoutBlankProfile() {
        FakeStore store = new FakeStore();
        store.loadFailure = new IllegalStateException("database unavailable");
        PlayerSessionManager sessions = new PlayerSessionManager(store, CLOCK);

        PlayerSessionSnapshot failed = sessions.open(PLAYER_ID, "Tester", 1).toCompletableFuture().join();

        assertEquals(PlayerSessionState.LOAD_FAILED, failed.state());
        assertTrue(failed.profile().isEmpty());
        assertEquals(0, sessions.activeSessionCount());
    }

    @Test
    void closingWhileLoadIsPendingInvalidatesLateCallback() {
        FakeStore store = new FakeStore();
        store.pendingLoad = new CompletableFuture<>();
        PlayerSessionManager sessions = new PlayerSessionManager(store, CLOCK);

        CompletionStage<PlayerSessionSnapshot> opening = sessions.open(PLAYER_ID, "Tester", 3);
        PlayerSessionSnapshot loading = sessions.snapshot(PLAYER_ID).orElseThrow();
        sessions.close(PLAYER_ID, loading.token()).toCompletableFuture().join();
        store.pendingLoad.complete(PlayerProfile.create(PLAYER_ID, "Tester", NOW));

        assertEquals(PlayerSessionState.CLOSED, opening.toCompletableFuture().join().state());
        assertTrue(sessions.snapshot(PLAYER_ID).isEmpty());
    }

    @Test
    void saveFailureRetainsRetryPendingSession() {
        FakeStore store = new FakeStore();
        store.saveFailure = new IllegalStateException("write failed");
        PlayerSessionManager sessions = new PlayerSessionManager(store, CLOCK);
        PlayerSessionSnapshot active = sessions.open(PLAYER_ID, "Tester", 1).toCompletableFuture().join();

        PlayerSessionSnapshot pending = sessions.close(PLAYER_ID, active.token()).toCompletableFuture().join();

        assertEquals(PlayerSessionState.SAVE_RETRY_PENDING, pending.state());
        assertTrue(pending.profile().isPresent());
        assertEquals(PlayerSessionState.SAVE_RETRY_PENDING, sessions.snapshot(PLAYER_ID).orElseThrow().state());
    }

    private static final class FakeStore implements PlayerProfileStore {
        private CompletableFuture<PlayerProfile> pendingLoad;
        private RuntimeException loadFailure;
        private RuntimeException saveFailure;
        private int saveCount;

        @Override
        public CompletionStage<PlayerProfile> loadOrCreate(UUID playerId, String name, Instant now) {
            if (pendingLoad != null) {
                return pendingLoad;
            }
            if (loadFailure != null) {
                return CompletableFuture.failedFuture(loadFailure);
            }
            return CompletableFuture.completedFuture(PlayerProfile.create(playerId, name, now));
        }

        @Override
        public CompletionStage<PlayerProfile> save(PlayerProfile profile) {
            saveCount++;
            if (saveFailure != null) {
                return CompletableFuture.failedFuture(saveFailure);
            }
            return CompletableFuture.completedFuture(profile.withRevision(profile.revision() + 1));
        }
    }
}
