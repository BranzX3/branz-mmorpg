package com.branz.mmorpg.bootstrap;

import java.util.Optional;

/** Prevents a new Scene snapshot from opening while canonical character truth is changing. */
final class SceneValueMutationAdmission {
    static final String IN_FLIGHT_MESSAGE =
            "Finish the pending character transaction before opening the Scene.";

    private SceneValueMutationAdmission() {}

    static Optional<String> rejection(boolean valueMutationInFlight) {
        return valueMutationInFlight ? Optional.of(IN_FLIGHT_MESSAGE) : Optional.empty();
    }
}
