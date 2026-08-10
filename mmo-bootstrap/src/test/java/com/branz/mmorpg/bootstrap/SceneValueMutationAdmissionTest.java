package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SceneValueMutationAdmissionTest {
    @Test
    void allowsSceneWhenCanonicalCharacterStateIsStable() {
        assertTrue(SceneValueMutationAdmission.rejection(false).isEmpty());
    }

    @Test
    void rejectsSceneWhileAuthoritativeValueMutationIsInFlight() {
        assertEquals(
                Optional.of(SceneValueMutationAdmission.IN_FLIGHT_MESSAGE),
                SceneValueMutationAdmission.rejection(true));
    }
}
