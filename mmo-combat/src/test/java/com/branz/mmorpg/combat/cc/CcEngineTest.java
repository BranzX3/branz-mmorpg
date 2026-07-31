package com.branz.mmorpg.combat.cc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class CcEngineTest {
    private final CcEngine engine = new CcEngine();

    @Test
    void strongerSeverityReplacesWhileEqualOrLowerRequiresContinuation() {
        CcRuntime runtime = CcRuntime.initial(0);
        CcApplication stagger =
                engine.apply(runtime, 0, request(CcSeverity.STAGGER, 20, false, false));
        assertEquals(CcApplicationOutcome.APPLIED, stagger.outcome());
        assertEquals(
                CcApplicationOutcome.REJECTED_ACTIVE,
                engine.apply(stagger.runtime(), 1, request(CcSeverity.FLINCH, 20, false, false))
                        .outcome());
        CcApplication replaced =
                engine.apply(stagger.runtime(), 1, request(CcSeverity.KNOCKDOWN, 30, false, false));
        assertEquals(CcApplicationOutcome.REPLACED, replaced.outcome());
        CcApplication continued =
                engine.apply(replaced.runtime(), 2, request(CcSeverity.KNOCKDOWN, 30, true, false));
        assertEquals(CcApplicationOutcome.CONTINUED, continued.outcome());
        assertEquals(15, continued.effectiveDurationTicks());
    }

    @Test
    void hardCcEndsIntoBoundedImmunityButStrongerSeverityCanBreakThrough() {
        CcApplication stagger =
                engine.apply(
                        CcRuntime.initial(0), 0, request(CcSeverity.STAGGER, 10, false, false));
        CcRuntime ended = engine.tick(stagger.runtime(), 10);
        assertTrue(ended.active().isEmpty());
        assertEquals(34, ended.immunityUntilTick());
        assertEquals(
                CcApplicationOutcome.REJECTED_IMMUNITY,
                engine.apply(ended, 11, request(CcSeverity.STAGGER, 10, false, false)).outcome());
        assertEquals(
                CcApplicationOutcome.APPLIED,
                engine.apply(ended, 11, request(CcSeverity.KNOCKDOWN, 10, false, false)).outcome());
        assertTrue(engine.tick(ended, 34).immunitySeverity().isEmpty());
    }

    @Test
    void pvpDurationAndRepeatedCategoryDiminishingReturnsTerminateTheChain() {
        CcRuntime runtime = CcRuntime.initial(0);
        CcApplication first = engine.apply(runtime, 0, request(CcSeverity.FLINCH, 10, false, true));
        assertEquals(6, first.effectiveDurationTicks());
        runtime = engine.tick(first.runtime(), 6);
        CcApplication second =
                engine.apply(runtime, 6, request(CcSeverity.FLINCH, 10, false, true));
        assertEquals(3, second.effectiveDurationTicks());
        runtime = engine.tick(second.runtime(), 9);
        CcApplication third = engine.apply(runtime, 9, request(CcSeverity.FLINCH, 10, false, true));
        assertEquals(CcApplicationOutcome.REJECTED_DIMINISHING_RETURNS, third.outcome());
        assertFalse(third.applied());

        runtime = engine.tick(third.runtime(), 169);
        CcApplication reset =
                engine.apply(runtime, 169, request(CcSeverity.FLINCH, 10, false, true));
        assertEquals(6, reset.effectiveDurationTicks());
    }

    @Test
    void seededCcSimulationNeverCreatesInvalidOrUnboundedRuntime() {
        Random random = new Random(0xCC20_2026L);
        for (int scenario = 0; scenario < 1_000; scenario++) {
            CcRuntime runtime = CcRuntime.initial(0);
            long tick = 0;
            for (int step = 0; step < 100; step++) {
                tick += random.nextInt(5);
                runtime = engine.tick(runtime, tick);
                CcRequest request =
                        request(
                                CcSeverity.values()[random.nextInt(CcSeverity.values().length)],
                                1 + random.nextInt(60),
                                random.nextInt(10) == 0,
                                random.nextBoolean());
                CcApplication result = engine.apply(runtime, tick, request);
                runtime = result.runtime();
                assertTrue(result.effectiveDurationTicks() >= 0);
                assertTrue(result.effectiveDurationTicks() <= 60);
                runtime.activeUntilTick();
            }
        }
    }

    private static CcRequest request(
            CcSeverity severity, int duration, boolean continuation, boolean pvp) {
        return new CcRequest(severity, duration, continuation, pvp);
    }
}
