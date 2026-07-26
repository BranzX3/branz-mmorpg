package com.branz.mmorpg.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.player.PlayerProfileComponent;
import com.branz.mmorpg.api.player.PlayerProfileRecoveryRecord;
import com.branz.mmorpg.api.player.PlayerProfileRecoveryStore;
import com.branz.mmorpg.api.player.PlayerProfileStore;
import com.branz.mmorpg.api.player.PlayerSessionSnapshot;
import com.branz.mmorpg.api.player.PlayerSessionState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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
    void duplicateOpenIsRejectedWhileTheFirstProfileIsStillLoading() {
        FakeStore store = new FakeStore();
        store.pendingLoad = new CompletableFuture<>();
        PlayerSessionManager sessions = new PlayerSessionManager(store, CLOCK);

        CompletionStage<PlayerSessionSnapshot> first = sessions.open(PLAYER_ID, "Tester", 1);
        PlayerSessionSnapshot duplicate = sessions.open(PLAYER_ID, "Tester", 1).toCompletableFuture().join();
        store.pendingLoad.complete(PlayerProfile.create(PLAYER_ID, "Tester", NOW));

        assertEquals(PlayerSessionState.CONFLICTED, duplicate.state());
        assertEquals(PlayerSessionState.ACTIVE, first.toCompletableFuture().join().state());
        assertEquals(1, sessions.activeSessionCount());
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
        assertEquals(3, store.saveCount);
    }

    @Test
    void profileMutationIsDirtyUntilPeriodicSaveCompletes() {
        FakeStore store = new FakeStore();
        PlayerSessionManager sessions = new PlayerSessionManager(store, CLOCK);
        PlayerSessionSnapshot active = sessions.open(PLAYER_ID, "Tester", 1).toCompletableFuture().join();

        PlayerSessionSnapshot dirty = sessions.updateProfile(
                PLAYER_ID,
                active.token(),
                PlayerProfileComponent.SETTINGS,
                profile -> profile.withSetting("hud", "compact"));

        assertEquals("compact", dirty.profile().orElseThrow().settings().get("hud"));
        assertEquals(java.util.Set.of(PlayerProfileComponent.SETTINGS), dirty.dirtyComponents());
        assertEquals(1, sessions.dirtySessionCount());

        PlayerSessionSnapshot saved = sessions.save(PLAYER_ID, active.token()).toCompletableFuture().join();

        assertEquals(PlayerSessionState.ACTIVE, saved.state());
        assertTrue(saved.dirtyComponents().isEmpty());
        assertEquals(1, saved.profile().orElseThrow().revision());
        assertEquals(0, sessions.dirtySessionCount());
    }

    @Test
    void profileMutationCannotHideAChangedComponent() {
        PlayerSessionManager sessions = new PlayerSessionManager(new FakeStore(), CLOCK);
        PlayerSessionSnapshot active = sessions.open(PLAYER_ID, "Tester", 1).toCompletableFuture().join();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> sessions.updateProfile(
                        PLAYER_ID,
                        active.token(),
                        PlayerProfileComponent.SETTINGS,
                        profile -> profile.withRespawnPoint(
                                Optional.of(com.branz.mmorpg.api.content.ContentId.parse("branz:spawn")))));

        assertTrue(failure.getMessage().contains("RESPAWN"));
        assertTrue(sessions.snapshot(PLAYER_ID).orElseThrow().dirtyComponents().isEmpty());
    }

    @Test
    void boundedRetryCanRecoverWithoutDroppingTheSession() {
        FakeStore store = new FakeStore();
        store.saveFailuresRemaining = 2;
        PlayerSessionManager sessions =
                new PlayerSessionManager(store, CLOCK, new PlayerSessionSavePolicy(3));
        PlayerSessionSnapshot active = sessions.open(PLAYER_ID, "Tester", 1).toCompletableFuture().join();
        sessions.updateProfile(
                PLAYER_ID,
                active.token(),
                PlayerProfileComponent.SETTINGS,
                profile -> profile.withSetting("language", "th_TH"));

        PlayerSessionSnapshot saved = sessions.save(PLAYER_ID, active.token()).toCompletableFuture().join();

        assertEquals(PlayerSessionState.ACTIVE, saved.state());
        assertEquals(3, store.saveCount);
        assertTrue(saved.dirtyComponents().isEmpty());
    }

    @Test
    void retryPendingSessionCanBeFlushedByASecondClose() {
        FakeStore store = new FakeStore();
        store.saveFailure = new IllegalStateException("write failed");
        PlayerSessionManager sessions =
                new PlayerSessionManager(store, CLOCK, new PlayerSessionSavePolicy(2));
        PlayerSessionSnapshot active = sessions.open(PLAYER_ID, "Tester", 1).toCompletableFuture().join();

        PlayerSessionSnapshot pending = sessions.close(PLAYER_ID, active.token()).toCompletableFuture().join();
        store.saveFailure = null;
        PlayerSessionSnapshot closed = sessions.close(PLAYER_ID, active.token()).toCompletableFuture().join();

        assertEquals(PlayerSessionState.SAVE_RETRY_PENDING, pending.state());
        assertEquals(PlayerSessionState.CLOSED, closed.state());
        assertTrue(sessions.snapshot(PLAYER_ID).isEmpty());
        assertEquals(3, store.saveCount);
    }

    @Test
    void quitDuringAnInFlightAutosaveClosesAfterThatSave() {
        FakeStore store = new FakeStore();
        store.pendingSave = new CompletableFuture<>();
        PlayerSessionManager sessions = new PlayerSessionManager(store, CLOCK);
        PlayerSessionSnapshot active = sessions.open(PLAYER_ID, "Tester", 1).toCompletableFuture().join();
        sessions.updateProfile(
                PLAYER_ID,
                active.token(),
                PlayerProfileComponent.SETTINGS,
                profile -> profile.withSetting("hud", "compact"));

        CompletionStage<PlayerSessionSnapshot> autosave = sessions.save(PLAYER_ID, active.token());
        CompletionStage<PlayerSessionSnapshot> close = sessions.close(PLAYER_ID, active.token());
        store.pendingSave.complete(active.profile().orElseThrow().withRevision(1));

        assertEquals(PlayerSessionState.CLOSED, autosave.toCompletableFuture().join().state());
        assertEquals(PlayerSessionState.CLOSED, close.toCompletableFuture().join().state());
        assertTrue(sessions.snapshot(PLAYER_ID).isEmpty());
    }

    @Test
    void exhaustedSaveWritesDurableRecoveryRecord() {
        FakeStore store = new FakeStore();
        store.saveFailure = new IllegalStateException("database offline");
        FakeRecoveryStore recovery = new FakeRecoveryStore();
        PlayerSessionManager sessions =
                new PlayerSessionManager(store, CLOCK, new PlayerSessionSavePolicy(1), recovery);
        PlayerSessionSnapshot active = sessions.open(PLAYER_ID, "Tester", 1).toCompletableFuture().join();
        sessions.updateProfile(
                PLAYER_ID,
                active.token(),
                PlayerProfileComponent.SETTINGS,
                profile -> profile.withSetting("hud", "compact"));

        PlayerSessionSnapshot pending = sessions.close(PLAYER_ID, active.token()).toCompletableFuture().join();

        assertEquals(PlayerSessionState.SAVE_RETRY_PENDING, pending.state());
        assertTrue(pending.detail().contains("durable recovery recorded"));
        assertEquals("compact", recovery.record.profile().settings().get("hud"));
    }

    @Test
    void recoveryJournalIsReplayedBeforeSessionBecomesActive() {
        FakeStore store = new FakeStore();
        FakeRecoveryStore recovery = new FakeRecoveryStore();
        recovery.record = new PlayerProfileRecoveryRecord(
                PlayerProfile.create(PLAYER_ID, "OldName", NOW).withSetting("hud", "compact"),
                NOW,
                "database offline");
        PlayerSessionManager sessions =
                new PlayerSessionManager(store, CLOCK, new PlayerSessionSavePolicy(1), recovery);

        PlayerSessionSnapshot active = sessions.open(PLAYER_ID, "Tester", 4).toCompletableFuture().join();

        assertEquals(PlayerSessionState.ACTIVE, active.state());
        assertEquals("compact", active.profile().orElseThrow().settings().get("hud"));
        assertEquals(1, active.profile().orElseThrow().revision());
        assertTrue(recovery.deleted);
    }

    private static final class FakeStore implements PlayerProfileStore {
        private CompletableFuture<PlayerProfile> pendingLoad;
        private RuntimeException loadFailure;
        private RuntimeException saveFailure;
        private int saveFailuresRemaining;
        private CompletableFuture<PlayerProfile> pendingSave;
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
            if (pendingSave != null) {
                return pendingSave;
            }
            if (saveFailure != null) {
                return CompletableFuture.failedFuture(saveFailure);
            }
            if (saveFailuresRemaining > 0) {
                saveFailuresRemaining--;
                return CompletableFuture.failedFuture(new IllegalStateException("transient write failure"));
            }
            return CompletableFuture.completedFuture(profile.withRevision(profile.revision() + 1));
        }
    }

    private static final class FakeRecoveryStore implements PlayerProfileRecoveryStore {
        private PlayerProfileRecoveryRecord record;
        private boolean deleted;

        @Override
        public CompletionStage<Optional<PlayerProfileRecoveryRecord>> load(UUID playerId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(record));
        }

        @Override
        public CompletionStage<Void> write(PlayerProfileRecoveryRecord nextRecord) {
            record = nextRecord;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> delete(UUID playerId) {
            deleted = true;
            record = null;
            return CompletableFuture.completedFuture(null);
        }
    }
}
