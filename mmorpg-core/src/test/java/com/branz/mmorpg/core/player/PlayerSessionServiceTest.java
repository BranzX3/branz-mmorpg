package com.branz.mmorpg.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.player.DuplicateLoginPolicy;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.player.PlayerSession;
import com.branz.mmorpg.api.player.SessionState;
import com.branz.mmorpg.api.player.SessionToken;
import com.branz.mmorpg.core.fixture.DirectScheduler;
import com.branz.mmorpg.core.fixture.FakePlayerProfileRepository;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerSessionServiceTest {

    private static final UUID PLAYER = UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");

    private FakePlayerProfileRepository repository;
    private FixedGameClock clock;
    private PlayerSessionService service;

    @BeforeEach
    void setUp() {
        repository = new FakePlayerProfileRepository();
        clock = FixedGameClock.at("2026-07-25T12:00:00Z");
        service = newService(DuplicateLoginPolicy.CLOSE_PREVIOUS);
        service.start();
    }

    @AfterEach
    void tearDown() {
        service.stop();
    }

    private PlayerSessionService newService(DuplicateLoginPolicy policy) {
        return new PlayerSessionService(repository, new DirectScheduler(), clock, () -> 7L, policy);
    }

    @Test
    void newPlayerGetsAnActiveSession() throws Exception {
        PlayerSession session = service.login(PLAYER, "Branz").get();

        assertEquals(SessionState.ACTIVE, session.state());
        assertTrue(session.playable());
        assertEquals(PLAYER, session.playerId());
        assertEquals(1L, session.token().sequence());
        assertEquals(7L, session.contentRevision());
        assertEquals("Branz", session.profile().lastKnownName());
        assertEquals(clock.now(), session.profile().lastSeenAt());
    }

    @Test
    void returningPlayerKeepsCreatedAtAndPicksUpANewName() throws Exception {
        service.login(PLAYER, "OldName").get();
        service.logout(PLAYER).get();
        PlayerProfile afterFirst = repository.stored(PLAYER);

        PlayerSession second = service.login(PLAYER, "NewName").get();

        assertEquals(afterFirst.createdAt(), second.profile().createdAt());
        assertEquals("NewName", second.profile().lastKnownName());
        assertEquals(2L, second.token().sequence(), "relogin takes a fresh token");
    }

    @Test
    void failedLoadNeverBecomesABlankProfile() {
        repository.failLoads(true);

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> service.login(PLAYER, "Branz").get());

        assertEquals(ErrorCode.PROFILE_LOAD_FAILED, ((MMOException) thrown.getCause()).code());
        assertEquals(SessionState.LOAD_FAILED, service.session(PLAYER).orElseThrow().state());
        assertEquals(ErrorCode.PROFILE_LOAD_FAILED,
                assertThrows(MMOException.class, () -> service.requirePlayable(PLAYER)).code());
        assertEquals(ErrorCode.PROFILE_LOAD_FAILED,
                assertThrows(MMOException.class,
                        () -> service.session(PLAYER).orElseThrow().profile()).code());
    }

    @Test
    void loadFailureLeavesNothingToSaveOnLogout() throws Exception {
        repository.failLoads(true);
        assertThrows(ExecutionException.class, () -> service.login(PLAYER, "Branz").get());

        service.logout(PLAYER).get();

        assertEquals(0, repository.saveCount(), "a profile that never loaded must never be written");
    }

    @Test
    void duplicateLoginConflictsThePreviousSession() throws Exception {
        PlayerSession first = service.login(PLAYER, "Branz").get();
        SessionToken firstToken = first.token();

        PlayerSession second = service.login(PLAYER, "Branz").get();

        assertEquals(SessionState.CONFLICTED, first.state());
        assertEquals(SessionState.ACTIVE, second.state());
        assertNotEquals(firstToken, second.token());
        assertTrue(firstToken.supersededBy(second.token()));
        assertFalse(service.isLive(firstToken), "the old token must stop being live");
        assertTrue(service.isLive(second.token()));
    }

    @Test
    void conflictedSessionRefusesMutation() throws Exception {
        RuntimePlayerSession first = (RuntimePlayerSession) service.login(PLAYER, "Branz").get();
        service.login(PLAYER, "Branz").get();

        MMOException refused = assertThrows(MMOException.class,
                () -> first.updateProfile(profile -> profile.withSetting("hud", "compact")));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, refused.code());
    }

    @Test
    void rejectNewPolicyRefusesTheSecondLogin() throws Exception {
        service.stop();
        service = newService(DuplicateLoginPolicy.REJECT_NEW);
        service.start();
        PlayerSession first = service.login(PLAYER, "Branz").get();

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> service.login(PLAYER, "Branz").get());

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ((MMOException) thrown.getCause()).code());
        assertEquals(SessionState.ACTIVE, first.state(), "the live session is untouched");
        assertTrue(service.isLive(first.token()));
    }

    @Test
    void logoutSavesAndClosesTheSession() throws Exception {
        RuntimePlayerSession session = (RuntimePlayerSession) service.login(PLAYER, "Branz").get();
        session.updateProfile(profile -> profile.withSetting("hud", "compact"));

        service.logout(PLAYER).get();

        assertEquals(SessionState.CLOSED, session.state());
        assertEquals("compact", repository.stored(PLAYER).setting("hud", "?"));
        assertTrue(service.session(PLAYER).isEmpty(), "nothing is retained after logout");
        assertFalse(service.isLive(session.token()));
    }

    @Test
    void logoutSaveRetriesAndThenKeepsADurableRecord() throws Exception {
        RuntimePlayerSession session = (RuntimePlayerSession) service.login(PLAYER, "Branz").get();
        session.updateProfile(profile -> profile.withSetting("hud", "compact"));
        repository.failNextSaves(PlayerSessionService.SAVE_ATTEMPTS);

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> service.logout(PLAYER).join());

        assertEquals(ErrorCode.STORAGE_FAILURE, ((MMOException) thrown.getCause()).code());
        assertEquals(PlayerSessionService.SAVE_ATTEMPTS, repository.saveCount(), "retries are bounded");
        assertTrue(service.pendingSaves().containsKey(PLAYER), "the write is retained, not lost");

        assertEquals(java.util.List.of(PLAYER), service.retryPendingSaves());
        assertEquals("compact", repository.stored(PLAYER).setting("hud", "?"));
        assertTrue(service.pendingSaves().isEmpty());
    }

    @Test
    void transientSaveFailureRecoversWithinTheRetryBudget() throws Exception {
        RuntimePlayerSession session = (RuntimePlayerSession) service.login(PLAYER, "Branz").get();
        session.updateProfile(profile -> profile.withSetting("hud", "compact"));
        repository.failNextSaves(PlayerSessionService.SAVE_ATTEMPTS - 1);

        service.logout(PLAYER).get();

        assertEquals(SessionState.CLOSED, session.state());
        assertTrue(service.pendingSaves().isEmpty());
        assertEquals("compact", repository.stored(PLAYER).setting("hud", "?"));
    }

    @Test
    void periodicFlushSavesOnlyDirtySessionsAndKeepsThemPlayable() throws Exception {
        RuntimePlayerSession session = (RuntimePlayerSession) service.login(PLAYER, "Branz").get();
        int afterLogin = repository.saveCount();

        assertEquals(1, service.flushAll(), "login marks the profile dirty");
        assertEquals(SessionState.ACTIVE, session.state(), "a periodic save resumes play");
        assertFalse(session.hasUnsavedChanges());

        assertEquals(0, service.flushAll(), "a clean session is not written again");
        assertEquals(afterLogin + 1, repository.saveCount());
    }

    @Test
    void shutdownFlushesDirtySessions() throws Exception {
        RuntimePlayerSession session = (RuntimePlayerSession) service.login(PLAYER, "Branz").get();
        session.updateProfile(profile -> profile.withSetting("hud", "compact"));

        service.stop();

        assertEquals("compact", repository.stored(PLAYER).setting("hud", "?"),
                "shutdown drains dirty state instead of discarding it");
        assertTrue(service.session(PLAYER).isEmpty());
    }

    @Test
    void staleTokenFromAPreviousLifeIsNotLive() throws Exception {
        PlayerSession first = service.login(PLAYER, "Branz").get();
        SessionToken stale = first.token();
        service.logout(PLAYER).get();
        service.login(PLAYER, "Branz").get();

        assertFalse(service.isLive(stale));
        assertFalse(service.isLive(null));
    }

    @Test
    void lifeSkillQueryFailsClosedForAnUnloadedPlayer() {
        assertEquals(ErrorCode.PROFILE_LOAD_FAILED,
                assertThrows(MMOException.class, () -> service.profile(PLAYER)).code());
    }
}
