package com.branz.mmorpg.combat.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.TestMoveFixtures;
import com.branz.mmorpg.combat.action.ActionPhase;
import com.branz.mmorpg.combat.action.CombatResources;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActionTimelineSimulatorTest {
    private final ActionTimelineSimulator simulator = new ActionTimelineSimulator();

    @Test
    void identicalInputsExportByteIdenticalDeterministicTrace() {
        CombatResources resources = CombatResources.full(1000, 100, 0);

        CombatTrace first =
                simulated(
                        resources,
                        List.of(
                                new ActionSimulationCommand(
                                        2, ActionSimulationCommand.Type.CANCEL, "STAGGER")));
        CombatTrace second =
                simulated(
                        resources,
                        List.of(
                                new ActionSimulationCommand(
                                        2, ActionSimulationCommand.Type.CANCEL, "STAGGER")));

        assertEquals(first, second);
        assertEquals(first.canonicalExport(), second.canonicalExport());
        assertEquals(ActionPhase.CANCELLED, first.finalPhase());
        assertEquals(100, first.finalResources().stamina());
    }

    @Test
    void fullTimelineTraceContainsCommitHitboxAndCompletionInOrder() {
        CombatTrace trace = simulated(CombatResources.full(1000, 100, 0), List.of());
        String exported = trace.canonicalExport();

        assertTrue(exported.indexOf("RESOURCES_COMMITTED") < exported.indexOf("HITBOX_OPENED"));
        assertTrue(exported.indexOf("HITBOX_OPENED") < exported.indexOf("ACTION_COMPLETED"));
        assertEquals(ActionPhase.COMPLETE, trace.finalPhase());
        assertEquals(88, trace.finalResources().stamina());
    }

    @Test
    void commandOutsideTimelineIsRejected() {
        Result<CombatTrace, CombatSimulationErrorCode> result =
                simulator.simulate(
                        "content.test",
                        TestMoveFixtures.trainingSlash(),
                        CombatResources.full(1000, 100, 0),
                        List.of(
                                new ActionSimulationCommand(
                                        14, ActionSimulationCommand.Type.CANCEL, "LATE")));

        assertEquals(
                CombatSimulationErrorCode.COMMAND_INVALID,
                ((Result.Failure<CombatTrace, CombatSimulationErrorCode>) result).error());
    }

    @Test
    void exportedTraceReplaysExactlyAndDetectsTampering() {
        CombatTrace trace = simulated(CombatResources.full(1000, 100, 0), List.of());

        Result<CombatTrace, CombatSimulationErrorCode> replayed =
                simulator.replay(trace, TestMoveFixtures.moveEngine());
        assertTrue(replayed.isSuccess());
        assertEquals(
                trace, ((Result.Success<CombatTrace, CombatSimulationErrorCode>) replayed).value());

        CombatTrace tampered =
                new CombatTrace(
                        trace.contentVersion(),
                        trace.moveId(),
                        trace.initialResources(),
                        trace.commands(),
                        trace.events().subList(0, trace.events().size() - 1),
                        trace.finalResources(),
                        trace.finalPhase());
        Result<CombatTrace, CombatSimulationErrorCode> rejected =
                simulator.replay(tampered, TestMoveFixtures.moveEngine());
        assertEquals(
                CombatSimulationErrorCode.TRACE_DIVERGED,
                ((Result.Failure<CombatTrace, CombatSimulationErrorCode>) rejected).error());
    }

    private CombatTrace simulated(
            CombatResources resources, List<ActionSimulationCommand> commands) {
        Result<CombatTrace, CombatSimulationErrorCode> result =
                simulator.simulate(
                        "content.test", TestMoveFixtures.trainingSlash(), resources, commands);
        assertTrue(result.isSuccess());
        return ((Result.Success<CombatTrace, CombatSimulationErrorCode>) result).value();
    }
}
