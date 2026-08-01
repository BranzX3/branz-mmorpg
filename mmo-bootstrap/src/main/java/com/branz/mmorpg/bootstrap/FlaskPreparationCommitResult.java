package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.combat.resource.FlaskPreparation;
import java.util.Objects;

record FlaskPreparationCommitResult(
        LoadedCharacterSession session, FlaskPreparation preparation, boolean replayed) {
    FlaskPreparationCommitResult {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(preparation, "preparation");
    }
}
