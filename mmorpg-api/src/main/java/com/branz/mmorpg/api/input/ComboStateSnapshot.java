package com.branz.mmorpg.api.input;

import com.branz.mmorpg.api.content.ContentId;
import java.util.UUID;

public record ComboStateSnapshot(UUID playerId, ContentId comboId, int acceptedSteps,
                                 long lastInputNanos, long expiresAtNanos,
                                 long loadoutRevision) {
}
