package com.branz.mmorpg.progression.renown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RenownEngineTest {
    private final RenownEngine engine = new RenownEngine();

    @Test
    void notableDeedAddsVisibleNonDecayingRenown() {
        RenownDecision decision = engine.evaluate(candidate(20), new RenownContext(75, 0, false));

        assertTrue(decision.accepted());
        assertEquals(20, decision.awardedRenown());
        assertEquals(95, decision.resultingRenown());
        assertEquals(1.0, decision.repetitionFactor());
    }

    @Test
    void identicalDailyDeedsDiminishThenStop() {
        assertEquals(
                10, engine.evaluate(candidate(20), new RenownContext(0, 1, false)).awardedRenown());
        assertEquals(
                5, engine.evaluate(candidate(20), new RenownContext(0, 2, false)).awardedRenown());
        RenownDecision exhausted = engine.evaluate(candidate(20), new RenownContext(40, 3, false));

        assertFalse(exhausted.accepted());
        assertEquals(40, exhausted.resultingRenown());
        assertEquals(
                RenownSuppressionReason.DAILY_REPETITION_EXHAUSTED, exhausted.suppressionReason());
    }

    @Test
    void duplicateIdIsIdempotentlySuppressed() {
        RenownDecision decision = engine.evaluate(candidate(20), new RenownContext(75, 0, true));

        assertFalse(decision.accepted());
        assertEquals(0, decision.awardedRenown());
        assertEquals(75, decision.resultingRenown());
        assertEquals(RenownSuppressionReason.DUPLICATE_DEED, decision.suppressionReason());
    }

    @Test
    void baseAwardIsStrictlyBounded() {
        assertThrows(IllegalArgumentException.class, () -> candidate(0));
        assertThrows(IllegalArgumentException.class, () -> candidate(101));
    }

    private static RenownDeedCandidate candidate(int baseRenown) {
        return new RenownDeedCandidate(
                UUID.randomUUID(),
                new CharacterId(UUID.randomUUID()),
                DefinitionId.of("renown.mentorship"),
                "teacher:student:technique:utc-day",
                baseRenown,
                "content-v1");
    }
}
