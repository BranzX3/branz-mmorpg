package com.branz.mmorpg.storage.player;

import java.sql.SQLException;
import java.util.UUID;

public final class PlayerProfileConflictException extends SQLException {
    public PlayerProfileConflictException(UUID playerId, long revision) {
        super("Player profile revision conflict for " + playerId + " at revision " + revision);
    }
}
