package com.branz.mmorpg.combat.engagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.combat.state.EngagementState;
import java.util.Random;
import org.junit.jupiter.api.Test;

class EngagementTrackerTest {
    private final EngagementTracker tracker = new EngagementTracker(160);

    @Test
    void threatAlertsUntilAHostileExchangeCommits() {
        EngagementRuntime runtime = EngagementRuntime.initial(10);

        runtime = tracker.alert(runtime, 11);
        assertEquals(EngagementState.ALERT, runtime.state());
        assertEquals(EngagementState.ALERT, tracker.tick(runtime, 12, threat()).state());
        assertEquals(
                EngagementState.EXPLORATION,
                tracker.tick(runtime, 13, EngagementTickContext.clear()).state());

        runtime = tracker.hostileActivity(runtime, 14);
        assertEquals(EngagementState.ENGAGED, runtime.state());
        assertEquals(14, runtime.lastHostileTick());
    }

    @Test
    void exitRequiresTheFullQuietWindowAndNoThreat() {
        EngagementRuntime runtime = tracker.hostileActivity(EngagementRuntime.initial(0), 20);

        runtime = tracker.tick(runtime, 21, EngagementTickContext.clear());
        assertEquals(EngagementState.DISENGAGING, runtime.state());
        assertEquals(159, tracker.remainingExitTicks(runtime, 21));
        assertEquals(
                EngagementState.DISENGAGING,
                tracker.tick(runtime, 179, EngagementTickContext.clear()).state());
        assertEquals(
                EngagementState.EXPLORATION,
                tracker.tick(runtime, 180, EngagementTickContext.clear()).state());
    }

    @Test
    void hostileActivityThreatHardLockAndDownedStateHoldOrRestoreEngagement() {
        EngagementRuntime runtime = tracker.hostileActivity(EngagementRuntime.initial(0), 10);
        runtime = tracker.tick(runtime, 11, EngagementTickContext.clear());
        assertEquals(EngagementState.DISENGAGING, runtime.state());

        runtime = tracker.tick(runtime, 12, threat());
        assertEquals(EngagementState.ENGAGED, runtime.state());
        assertEquals(10, runtime.lastHostileTick());

        runtime = tracker.tick(runtime, 200, threat());
        assertEquals(EngagementState.ENGAGED, runtime.state());
        runtime = tracker.tick(runtime, 201, new EngagementTickContext(false, true, false));
        assertEquals(EngagementState.ENGAGED, runtime.state());
        runtime = tracker.tick(runtime, 202, new EngagementTickContext(false, false, true));
        assertEquals(EngagementState.ENGAGED, runtime.state());

        runtime = tracker.hostileActivity(runtime, 203);
        assertEquals(203, runtime.lastHostileTick());
        assertEquals(160, tracker.remainingExitTicks(runtime, 203));
    }

    @Test
    void sameServerTickSequenceIsDeterministic() {
        EngagementRuntime first = simulate();
        EngagementRuntime second = simulate();

        assertEquals(first, second);
        assertEquals(EngagementState.EXPLORATION, first.state());
    }

    @Test
    void randomizedServerTickSequencesRemainDeterministicAndBounded() {
        Random random = new Random(0xB12A_2026L);
        for (int scenario = 0; scenario < 1_000; scenario++) {
            EngagementRuntime first = EngagementRuntime.initial(0);
            EngagementRuntime second = EngagementRuntime.initial(0);
            long tick = 0;
            for (int step = 0; step < 100; step++) {
                tick += random.nextInt(4);
                int event = random.nextInt(5);
                if (event == 0) {
                    first = tracker.alert(first, tick);
                    second = tracker.alert(second, tick);
                } else if (event == 1) {
                    first = tracker.hostileActivity(first, tick);
                    second = tracker.hostileActivity(second, tick);
                } else {
                    EngagementTickContext context =
                            new EngagementTickContext(event == 2, event == 3, event == 4);
                    first = tracker.tick(first, tick, context);
                    second = tracker.tick(second, tick, context);
                }

                assertEquals(first, second);
                int remaining = tracker.remainingExitTicks(first, tick);
                assertTrue(remaining >= 0 && remaining <= tracker.exitTicks());
            }
        }
    }

    private EngagementRuntime simulate() {
        EngagementRuntime runtime = EngagementRuntime.initial(0);
        runtime = tracker.alert(runtime, 3);
        runtime = tracker.hostileActivity(runtime, 5);
        runtime = tracker.tick(runtime, 6, EngagementTickContext.clear());
        runtime = tracker.hostileActivity(runtime, 60);
        runtime = tracker.tick(runtime, 61, EngagementTickContext.clear());
        return tracker.tick(runtime, 220, EngagementTickContext.clear());
    }

    private static EngagementTickContext threat() {
        return new EngagementTickContext(true, false, false);
    }
}
