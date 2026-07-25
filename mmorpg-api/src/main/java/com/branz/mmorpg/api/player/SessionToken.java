package com.branz.mmorpg.api.player;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifies one login of one player.
 *
 * <p>The {@code sequence} increases monotonically per player, so a relogin always
 * produces a token that is distinct from and greater than the previous one.
 *
 * <p>This is the guard for late async callbacks. Work that started before a
 * player logged out must compare its captured token against the live session
 * before touching anything: without it, a database read that completes after a
 * fast relogin writes the previous life's state over the new session.
 *
 * @param playerId player this login belongs to
 * @param sequence per-player login counter, starting at 1
 */
public record SessionToken(UUID playerId, long sequence) implements Comparable<SessionToken> {

    public SessionToken {
        Objects.requireNonNull(playerId, "playerId");
        if (sequence < 1) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT, "sequence must be at least 1: " + sequence);
        }
    }

    public static SessionToken first(UUID playerId) {
        return new SessionToken(playerId, 1L);
    }

    public SessionToken next() {
        return new SessionToken(playerId, sequence + 1);
    }

    /** Whether {@code other} is a later login of the same player. */
    public boolean supersededBy(SessionToken other) {
        return other != null && other.playerId.equals(playerId) && other.sequence > sequence;
    }

    @Override
    public int compareTo(SessionToken other) {
        int byPlayer = playerId.compareTo(other.playerId);
        return byPlayer != 0 ? byPlayer : Long.compare(sequence, other.sequence);
    }

    @Override
    public String toString() {
        return playerId + "#" + sequence;
    }
}
