package com.branz.mmorpg.combat.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.TestMoveFixtures;
import com.branz.mmorpg.combat.move.MoveDefinition;
import org.junit.jupiter.api.Test;

class ActionTimelineTest {
    @Test
    void cancellationBeforeCommitReleasesReservationWithoutSpendingMainCost() {
        MoveDefinition move = TestMoveFixtures.trainingSlash();
        CombatResources initial = CombatResources.full(1000, 100, 0);
        ActionTimeline timeline = started(move, initial);
        assertEquals(100, timeline.resources().stamina());
        assertEquals(12, timeline.resources().reservedStamina());

        timeline = advanced(timeline);
        timeline = advanced(timeline);
        timeline = cancelled(timeline, "STAGGER");

        assertEquals(ActionPhase.CANCELLED, timeline.phase());
        assertEquals(ResourceCommitState.RELEASED, timeline.resourceState());
        assertEquals(100, timeline.resources().stamina());
        assertEquals(0, timeline.resources().reservedStamina());
    }

    @Test
    void setupCostIsTheOnlyPreCommitCancellationSpend() {
        MoveDefinition move =
                TestMoveFixtures.trainingSlash(new MoveDefinition.ResourceCost(12, 0, 0, 3));
        ActionTimeline cancelled =
                cancelled(started(move, CombatResources.full(1000, 100, 0)), "BLOCKED_MOTION");

        assertEquals(97, cancelled.resources().stamina());
        assertEquals(0, cancelled.resources().reservedStamina());
        assertTrue(
                cancelled.trace().stream()
                        .anyMatch(
                                event ->
                                        event.type() == ActionTraceEventType.SETUP_COST_COMMITTED));
    }

    @Test
    void cancellationAfterCommitKeepsSpentResources() {
        ActionTimeline timeline =
                started(TestMoveFixtures.trainingSlash(), CombatResources.full(1000, 100, 0));
        timeline = advanced(timeline);
        timeline = advanced(timeline);
        timeline = advanced(timeline);
        assertEquals(ResourceCommitState.COMMITTED, timeline.resourceState());
        assertEquals(88, timeline.resources().stamina());

        timeline = cancelled(timeline, "HEAVY_STAGGER");

        assertEquals(88, timeline.resources().stamina());
        assertEquals(ResourceCommitState.COMMITTED, timeline.resourceState());
    }

    @Test
    void activeHitboxAndPhaseTransitionsOccurOnlyAtAuthoredTicks() {
        MoveDefinition move = TestMoveFixtures.trainingSlash();
        ActionTimeline timeline = started(move, CombatResources.full(1000, 100, 0));
        while (!timeline.phase().terminal()) {
            timeline = advanced(timeline);
        }

        assertEquals(ActionPhase.COMPLETE, timeline.phase());
        assertEquals(14, timeline.tick());
        assertEquals(88, timeline.resources().stamina());
        assertEquals(
                1,
                timeline.trace().stream()
                        .filter(event -> event.type() == ActionTraceEventType.HITBOX_OPENED)
                        .count());
        ActionTraceEvent hitbox =
                timeline.trace().stream()
                        .filter(event -> event.type() == ActionTraceEventType.HITBOX_OPENED)
                        .findFirst()
                        .orElseThrow();
        assertEquals(4, hitbox.tick());
    }

    @Test
    void chainAndDodgeWindowsUseCurrentAuthoritativeTick() {
        ActionTimeline timeline =
                started(TestMoveFixtures.trainingSlash(), CombatResources.full(1000, 100, 0));
        for (int tick = 0; tick < 7; tick++) {
            timeline = advanced(timeline);
        }
        assertTrue(timeline.chainWindowOpen("PRIMARY_2"));
        assertTrue(!timeline.dodgeCancelOpen());
        timeline = advanced(timeline);
        timeline = advanced(timeline);
        assertTrue(timeline.dodgeCancelOpen());
    }

    @Test
    void insufficientResourceRejectsBeforeTimelineExists() {
        Result<ActionTimeline, ActionTimelineErrorCode> result =
                ActionTimeline.start(
                        TestMoveFixtures.trainingSlash(), CombatResources.full(1000, 11, 0));

        assertEquals(
                ActionTimelineErrorCode.NO_STAMINA,
                ((Result.Failure<ActionTimeline, ActionTimelineErrorCode>) result).error());
    }

    private static ActionTimeline started(MoveDefinition move, CombatResources resources) {
        Result<ActionTimeline, ActionTimelineErrorCode> result =
                ActionTimeline.start(move, resources);
        assertTrue(result.isSuccess());
        return ((Result.Success<ActionTimeline, ActionTimelineErrorCode>) result).value();
    }

    private static ActionTimeline advanced(ActionTimeline timeline) {
        Result<ActionTimeline, ActionTimelineErrorCode> result = timeline.advance();
        assertTrue(result.isSuccess());
        return ((Result.Success<ActionTimeline, ActionTimelineErrorCode>) result).value();
    }

    private static ActionTimeline cancelled(ActionTimeline timeline, String reason) {
        Result<ActionTimeline, ActionTimelineErrorCode> result = timeline.cancel(reason);
        assertTrue(result.isSuccess());
        return ((Result.Success<ActionTimeline, ActionTimelineErrorCode>) result).value();
    }
}
