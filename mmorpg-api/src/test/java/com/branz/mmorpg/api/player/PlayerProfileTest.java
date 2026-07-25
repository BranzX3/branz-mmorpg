package com.branz.mmorpg.api.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerProfileTest {

    private static final UUID PLAYER = UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    @Test
    void refusesAProfileWrittenByANewerBuild() {
        MMOException refused = assertThrows(MMOException.class,
                () -> new PlayerProfile(PLAYER, "Branz", PlayerProfile.CURRENT_SCHEMA_VERSION + 1,
                        NOW, NOW, Optional.empty(), Optional.empty(), Map.of()));

        assertEquals(ErrorCode.STORAGE_FAILURE, refused.code());
    }

    @Test
    void settingsAreCopiedAndUpdatesDoNotMutateTheOriginal() {
        PlayerProfile profile = PlayerProfile.createNew(PLAYER, "Branz", NOW)
                .withSetting("hud", "compact");

        PlayerProfile updated = profile.withSetting("hud", "full");

        assertEquals("compact", profile.setting("hud", "?"));
        assertEquals("full", updated.setting("hud", "?"));
        assertThrows(UnsupportedOperationException.class, () -> profile.settings().put("hud", "x"));
        assertTrue(profile.withSetting("hud", null).settings().isEmpty());
    }

    @Test
    void nameIsPresentationAndNeverIdentity() {
        PlayerProfile renamed = PlayerProfile.createNew(PLAYER, "OldName", NOW).withName("NewName");

        assertEquals(PLAYER, renamed.playerId(), "identity survives a rename");
        assertEquals("NewName", renamed.lastKnownName());
    }

    @Test
    void sessionStateAllowsOnlyDocumentedTransitions() {
        assertTrue(SessionState.ABSENT.canTransitionTo(SessionState.LOADING));
        assertTrue(SessionState.LOADING.canTransitionTo(SessionState.ACTIVE));
        assertTrue(SessionState.LOADING.canTransitionTo(SessionState.LOAD_FAILED));
        assertTrue(SessionState.SAVING.canTransitionTo(SessionState.SAVE_RETRY_PENDING));
        assertTrue(SessionState.ACTIVE.canTransitionTo(SessionState.CONFLICTED));

        assertFalse(SessionState.ABSENT.canTransitionTo(SessionState.ACTIVE), "no skipping the load");
        assertFalse(SessionState.LOAD_FAILED.canTransitionTo(SessionState.ACTIVE),
                "a failed load can never become playable without a fresh login");
        assertFalse(SessionState.CLOSED.canTransitionTo(SessionState.ACTIVE));
        assertFalse(SessionState.CONFLICTED.canTransitionTo(SessionState.SAVING),
                "a superseded session must never write");

        assertTrue(SessionState.ACTIVE.playable());
        assertFalse(SessionState.SAVING.playable());
        assertTrue(SessionState.LOAD_FAILED.loadFailure());
    }

    @Test
    void sessionTokensOrderPerPlayerLogins() {
        SessionToken first = SessionToken.first(PLAYER);
        SessionToken second = first.next();

        assertTrue(first.supersededBy(second));
        assertFalse(second.supersededBy(first));
        assertFalse(first.supersededBy(SessionToken.first(UUID.randomUUID())),
                "another player's token never supersedes this one");
        assertTrue(first.compareTo(second) < 0);
        assertEquals(ErrorCode.INVALID_ARGUMENT,
                assertThrows(MMOException.class, () -> new SessionToken(PLAYER, 0)).code());
    }
}
