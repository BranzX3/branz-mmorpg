package com.branz.mmorpg.lifeskills.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LifeskillProgressionTest {
    private final LifeskillRankTable ranks = rankTable();
    private final LifeskillRankEngine rankEngine = new LifeskillRankEngine(ranks);
    private final LifeFocusEngine focusEngine = new LifeFocusEngine();

    @Test
    void exposesAllThirtyAuthoredRanksWithoutInventingBalanceThresholds() {
        assertEquals("Trainee I", LifeskillRank.initial().displayName());
        assertEquals("Grandmaster V", LifeskillRank.fromOrdinal(29).displayName());
        assertTrue(LifeskillRank.fromOrdinal(29).next().isEmpty());
        assertEquals(new LifeskillRank(LifeskillRankTier.SKILLED, 1), ranks.rankAt(50));
        assertEquals(60, ranks.nextThreshold(ranks.rankAt(50)).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> LifeskillRank.fromOrdinal(30));
        assertThrows(
                IllegalArgumentException.class, () -> new LifeskillRankTable(List.of(0.0, 10.0)));
    }

    @Test
    void committedRankEvidencePromotesAndReplaysExactly() {
        LifeskillRankRuntime initial =
                LifeskillRankRuntime.initial(LifeskillDiscipline.of("mining"));
        UUID operationId = UUID.randomUUID();

        LifeskillRankDecision promoted =
                success(rankEngine.applyCommittedEvidence(initial, 75, operationId));
        assertEquals(75, promoted.runtime().evidence());
        assertEquals(new LifeskillRank(LifeskillRankTier.SKILLED, 3), promoted.runtime().rank());
        assertTrue(promoted.promoted());
        assertFalse(promoted.replayed());

        LifeskillRankDecision replay =
                success(rankEngine.applyCommittedEvidence(promoted.runtime(), 75, operationId));
        assertTrue(replay.replayed());
        assertEquals(promoted.runtime(), replay.runtime());
        assertEquals(
                LifeskillProgressionErrorCode.OPERATION_ID_REUSED,
                failure(rankEngine.applyCommittedEvidence(promoted.runtime(), 76, operationId)));
    }

    @Test
    void rankEvidenceCapsAtGrandmasterAndRejectsInconsistentRuntime() {
        LifeskillRankRuntime initial =
                LifeskillRankRuntime.initial(LifeskillDiscipline.of("logging"));
        LifeskillRankDecision maximum =
                success(rankEngine.applyCommittedEvidence(initial, 10_000, UUID.randomUUID()));
        assertEquals(290, maximum.runtime().evidence());
        assertEquals("Grandmaster V", maximum.runtime().rank().displayName());
        assertEquals(
                LifeskillProgressionErrorCode.EVIDENCE_INVALID,
                failure(
                        rankEngine.applyCommittedEvidence(
                                maximum.runtime(), 1, UUID.randomUUID())));

        LifeskillRankRuntime corrupt =
                new LifeskillRankRuntime(
                        initial.discipline(), 100, LifeskillRank.initial(), Map.of());
        assertEquals(
                LifeskillProgressionErrorCode.RUNTIME_INVALID,
                failure(rankEngine.applyCommittedEvidence(corrupt, 1, UUID.randomUUID())));
    }

    @Test
    void masteryCompositionIsVisibleDiminishingAndHardCapped() {
        LifeskillMasteryEngine engine = new LifeskillMasteryEngine();
        LifeskillMasteryProfile zero = engine.resolve(new LifeskillMasteryInputs(0, 0, 0, 0, 0, 0));
        LifeskillMasteryProfile middle =
                engine.resolve(new LifeskillMasteryInputs(200, 100, 75, 50, 50, 25));
        LifeskillMasteryProfile capped =
                engine.resolve(new LifeskillMasteryInputs(1000, 1000, 1000, 0, 0, 0));

        assertEquals(0, zero.mastery());
        assertEquals(500, middle.mastery());
        assertEquals(0.2625, middle.workSpeedBonus(), 0.000001);
        assertEquals(0.45, middle.basicYieldBonus(), 0.000001);
        assertEquals(0.225, middle.rareYieldRelativeBonus(), 0.000001);
        assertEquals(1000, capped.mastery());
        assertEquals(LifeskillMasteryEngine.WORK_SPEED_BONUS_CAP, capped.workSpeedBonus());
        assertEquals(LifeskillMasteryEngine.BASIC_YIELD_BONUS_CAP, capped.basicYieldBonus());
        assertEquals(
                LifeskillMasteryEngine.RARE_YIELD_RELATIVE_BONUS_CAP,
                capped.rareYieldRelativeBonus());
    }

    @Test
    void focusRecoversOfflineWithoutBankingBeyondItsCap() {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        LifeFocusRuntime spent = new LifeFocusRuntime(95, start, Map.of());
        LifeFocusRuntime capped =
                success(focusEngine.recover(spent, start.plus(70, ChronoUnit.MINUTES)));
        assertEquals(100, capped.focus());
        assertEquals(start.plus(70, ChronoUnit.MINUTES), capped.lastRecoveryAt());

        LifeFocusDecision afterCap =
                success(
                        focusEngine.commitWork(
                                capped, 5, UUID.randomUUID(), start.plus(70, ChronoUnit.MINUTES)));
        LifeFocusRuntime nineMinutes =
                success(
                        focusEngine.recover(
                                afterCap.runtime(), start.plus(79, ChronoUnit.MINUTES)));
        assertEquals(95, nineMinutes.focus());
        LifeFocusRuntime tenMinutes =
                success(
                        focusEngine.recover(
                                afterCap.runtime(), start.plus(80, ChronoUnit.MINUTES)));
        assertEquals(96, tenMinutes.focus());
    }

    @Test
    void zeroFocusAllowsNormalWorkAndFocusedSpendIsExact() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        LifeFocusRuntime empty = new LifeFocusRuntime(0, now, Map.of());
        UUID normalOperation = UUID.randomUUID();
        LifeFocusDecision normal = success(focusEngine.commitWork(empty, 0, normalOperation, now));
        assertFalse(normal.focusedWork());
        assertEquals(0, normal.runtime().focus());

        LifeFocusRuntime available = new LifeFocusRuntime(5, now, Map.of());
        UUID focusedOperation = UUID.randomUUID();
        LifeFocusDecision focused =
                success(focusEngine.commitWork(available, 5, focusedOperation, now));
        assertTrue(focused.focusedWork());
        assertEquals(0, focused.runtime().focus());
        assertTrue(
                success(focusEngine.commitWork(focused.runtime(), 5, focusedOperation, now))
                        .replayed());
        assertEquals(
                LifeFocusErrorCode.OPERATION_ID_REUSED,
                failure(focusEngine.commitWork(focused.runtime(), 4, focusedOperation, now)));
        assertEquals(
                LifeFocusErrorCode.FOCUS_INSUFFICIENT,
                failure(focusEngine.commitWork(empty, 1, UUID.randomUUID(), now)));
    }

    @Test
    void focusRejectsInvalidCostAndBackwardClock() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        LifeFocusRuntime runtime = LifeFocusRuntime.full(now);
        assertEquals(
                LifeFocusErrorCode.COST_INVALID,
                failure(focusEngine.commitWork(runtime, 6, UUID.randomUUID(), now)));
        assertEquals(
                LifeFocusErrorCode.CLOCK_MOVED_BACKWARD,
                failure(focusEngine.recover(runtime, now.minusSeconds(1))));
    }

    @Test
    void disciplineIdentityRequiresStableLifeskillNamespace() {
        assertEquals("lifeskill.fishing", LifeskillDiscipline.of("fishing").id().value());
        assertThrows(
                IllegalArgumentException.class,
                () -> new LifeskillDiscipline(DefinitionId.of("mastery.fishing")));
    }

    private static LifeskillRankTable rankTable() {
        return new LifeskillRankTable(
                IntStream.range(0, LifeskillRank.RANK_COUNT)
                        .mapToObj(index -> index * 10.0)
                        .toList());
    }

    private static <T, E extends com.branz.mmorpg.api.result.ErrorCode> T success(
            Result<T, E> result) {
        return ((Result.Success<T, E>) result).value();
    }

    private static <T, E extends com.branz.mmorpg.api.result.ErrorCode> E failure(
            Result<T, E> result) {
        return ((Result.Failure<T, E>) result).error();
    }
}
