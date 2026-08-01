package com.branz.mmorpg.bootstrap;

import java.util.Objects;

record ConsumableUseCommitResult(
        LoadedCharacterSession session, PersistentConsumableEffect effect, boolean replayed) {
    ConsumableUseCommitResult {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(effect, "effect");
    }
}
