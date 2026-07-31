package com.branz.mmorpg.combat.trace;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.action.ActionTimeline;
import com.branz.mmorpg.combat.action.ActionTimelineErrorCode;
import com.branz.mmorpg.combat.action.CombatResources;
import com.branz.mmorpg.combat.move.MoveDefinition;
import com.branz.mmorpg.combat.move.MoveEngine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure deterministic simulation and replay used by tests and the future Combat Lab. */
public final class ActionTimelineSimulator {
    public Result<CombatTrace, CombatSimulationErrorCode> simulate(
            String contentVersion,
            MoveDefinition move,
            CombatResources initialResources,
            List<ActionSimulationCommand> commands) {
        Objects.requireNonNull(contentVersion, "contentVersion");
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(initialResources, "initialResources");
        Objects.requireNonNull(commands, "commands");
        List<ActionSimulationCommand> ordered =
                commands.stream()
                        .sorted(Comparator.comparingInt(ActionSimulationCommand::tick))
                        .toList();
        Map<Integer, ActionSimulationCommand> byTick = new HashMap<>();
        for (ActionSimulationCommand command : ordered) {
            if (command.tick() >= move.phases().totalTicks()
                    || byTick.putIfAbsent(command.tick(), command) != null) {
                return Result.failure(
                        CombatSimulationErrorCode.COMMAND_INVALID,
                        "Commands must target a unique tick inside the action timeline.");
            }
        }
        Result<ActionTimeline, ActionTimelineErrorCode> started =
                ActionTimeline.start(move, initialResources);
        if (started instanceof Result.Failure<ActionTimeline, ActionTimelineErrorCode> failure) {
            return Result.failure(
                    CombatSimulationErrorCode.RESOURCE_REJECTED,
                    failure.error().code() + ": " + failure.detail());
        }
        ActionTimeline timeline =
                ((Result.Success<ActionTimeline, ActionTimelineErrorCode>) started).value();
        while (!timeline.phase().terminal()) {
            ActionSimulationCommand command = byTick.get(timeline.tick());
            Result<ActionTimeline, ActionTimelineErrorCode> next =
                    command == null ? timeline.advance() : timeline.cancel(command.detail());
            if (next instanceof Result.Failure<ActionTimeline, ActionTimelineErrorCode> failure) {
                return Result.failure(
                        CombatSimulationErrorCode.COMMAND_INVALID,
                        failure.error().code() + ": " + failure.detail());
            }
            timeline = ((Result.Success<ActionTimeline, ActionTimelineErrorCode>) next).value();
        }
        return Result.success(
                new CombatTrace(
                        contentVersion,
                        move.id(),
                        initialResources,
                        new ArrayList<>(ordered),
                        timeline.trace(),
                        timeline.resources(),
                        timeline.phase()));
    }

    public Result<CombatTrace, CombatSimulationErrorCode> replay(
            CombatTrace expected, MoveEngine engine) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(engine, "engine");
        MoveDefinition move = engine.find(expected.moveId()).orElse(null);
        if (move == null) {
            return Result.failure(
                    CombatSimulationErrorCode.MOVE_NOT_FOUND,
                    "Trace move is absent from the supplied content snapshot.");
        }
        Result<CombatTrace, CombatSimulationErrorCode> replayed =
                simulate(
                        expected.contentVersion(),
                        move,
                        expected.initialResources(),
                        expected.commands());
        if (replayed instanceof Result.Failure<CombatTrace, CombatSimulationErrorCode>) {
            return replayed;
        }
        CombatTrace actual =
                ((Result.Success<CombatTrace, CombatSimulationErrorCode>) replayed).value();
        if (!actual.equals(expected)) {
            return Result.failure(
                    CombatSimulationErrorCode.TRACE_DIVERGED,
                    "Replayed authoritative events differ from the exported trace.");
        }
        return Result.success(actual);
    }
}
