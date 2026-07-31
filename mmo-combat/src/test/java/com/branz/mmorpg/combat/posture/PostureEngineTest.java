package com.branz.mmorpg.combat.posture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class PostureEngineTest {
    private final PostureEngine engine = new PostureEngine(PostureProfile.trainingNormal());

    @Test
    void damageBreakAndAuthoredRecoveryAreServerTickDeterministic() {
        PostureRuntime runtime = PostureRuntime.initial(engine.profile(), 0);
        PostureResolution damaged = engine.damage(runtime, 0, 12);
        assertEquals(88, damaged.runtime().current());
        assertFalse(damaged.justBroke());
        assertEquals(88, engine.tick(damaged.runtime(), 59).current());
        assertEquals(89, engine.tick(damaged.runtime(), 60).current());

        PostureResolution broken = engine.damage(runtime, 0, 100);
        assertTrue(broken.justBroke());
        assertEquals(PosturePhase.BROKEN, engine.phaseAt(broken.runtime(), 59));
        assertEquals(0, engine.damage(broken.runtime(), 30, 999).runtime().current());
        PostureRuntime recovered = engine.tick(broken.runtime(), 60);
        assertEquals(PosturePhase.STABLE, engine.phaseAt(recovered, 60));
        assertEquals(100, recovered.current());
    }

    @Test
    void randomizedDamageAndTickSequencesStayWithinProfileBounds() {
        Random random = new Random(0x5057_2026L);
        for (int scenario = 0; scenario < 1_000; scenario++) {
            PostureRuntime runtime = PostureRuntime.initial(engine.profile(), 0);
            long tick = 0;
            for (int step = 0; step < 100; step++) {
                tick += random.nextInt(4);
                if (random.nextBoolean()) {
                    runtime = engine.damage(runtime, tick, random.nextDouble(30)).runtime();
                } else {
                    runtime = engine.tick(runtime, tick);
                }
                assertTrue(runtime.current() >= 0);
                assertTrue(runtime.current() <= engine.profile().maximum());
            }
        }
    }
}
