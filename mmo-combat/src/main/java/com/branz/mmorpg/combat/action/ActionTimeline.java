package com.branz.mmorpg.combat.action;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.move.MoveDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable integer-tick action with explicit resource reservation and commit boundary. */
public record ActionTimeline(
        MoveDefinition move,
        int tick,
        ActionPhase phase,
        ResourceCommitState resourceState,
        CombatResources resources,
        List<ActionTraceEvent> trace) {
    public ActionTimeline {
        Objects.requireNonNull(move, "move");
        if (tick < 0 || tick > move.phases().totalTicks()) {
            throw new IllegalArgumentException("action tick is outside its timeline");
        }
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(resourceState, "resourceState");
        Objects.requireNonNull(resources, "resources");
        trace = List.copyOf(Objects.requireNonNull(trace, "trace"));
        if (phase == ActionPhase.COMPLETE && tick != move.phases().totalTicks()) {
            throw new IllegalArgumentException("complete action must be at total timeline ticks");
        }
        if (phase == ActionPhase.CANCELLED && resourceState == ResourceCommitState.RESERVED) {
            throw new IllegalArgumentException("cancelled action cannot retain a reservation");
        }
    }

    public static Result<ActionTimeline, ActionTimelineErrorCode> start(
            MoveDefinition move, CombatResources resources) {
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(resources, "resources");
        Result<CombatResources, ActionTimelineErrorCode> reserved = resources.reserve(move.costs());
        if (reserved instanceof Result.Failure<CombatResources, ActionTimelineErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        CombatResources next =
                ((Result.Success<CombatResources, ActionTimelineErrorCode>) reserved).value();
        ArrayList<ActionTraceEvent> events = new ArrayList<>();
        events.add(new ActionTraceEvent(0, ActionTraceEventType.ACTION_STARTED, move.id().value()));
        events.add(
                new ActionTraceEvent(
                        0, ActionTraceEventType.RESOURCES_RESERVED, costDetail(move.costs())));
        ResourceCommitState commitState = ResourceCommitState.RESERVED;
        if (move.commitTick() == 0) {
            next = next.commit(move.costs());
            commitState = ResourceCommitState.COMMITTED;
            events.add(
                    new ActionTraceEvent(
                            0, ActionTraceEventType.RESOURCES_COMMITTED, costDetail(move.costs())));
        }
        ActionPhase phase = phaseAt(move, 0);
        events.add(new ActionTraceEvent(0, ActionTraceEventType.PHASE_CHANGED, phase.name()));
        appendHitboxes(move, 0, events);
        return Result.success(new ActionTimeline(move, 0, phase, commitState, next, events));
    }

    public Result<ActionTimeline, ActionTimelineErrorCode> advance() {
        if (phase.terminal()) {
            return Result.failure(
                    ActionTimelineErrorCode.ACTION_ALREADY_FINISHED,
                    "Terminal action timeline cannot advance.");
        }
        int nextTick = tick + 1;
        ArrayList<ActionTraceEvent> events = new ArrayList<>(trace);
        if (nextTick == move.phases().totalTicks()) {
            events.add(
                    new ActionTraceEvent(
                            nextTick, ActionTraceEventType.ACTION_COMPLETED, move.id().value()));
            return Result.success(
                    new ActionTimeline(
                            move,
                            nextTick,
                            ActionPhase.COMPLETE,
                            resourceState,
                            resources,
                            events));
        }
        CombatResources nextResources = resources;
        ResourceCommitState nextResourceState = resourceState;
        if (resourceState == ResourceCommitState.RESERVED && nextTick == move.commitTick()) {
            nextResources = resources.commit(move.costs());
            nextResourceState = ResourceCommitState.COMMITTED;
            events.add(
                    new ActionTraceEvent(
                            nextTick,
                            ActionTraceEventType.RESOURCES_COMMITTED,
                            costDetail(move.costs())));
        }
        ActionPhase nextPhase = phaseAt(move, nextTick);
        if (nextPhase != phase) {
            events.add(
                    new ActionTraceEvent(
                            nextTick, ActionTraceEventType.PHASE_CHANGED, nextPhase.name()));
        }
        appendHitboxes(move, nextTick, events);
        return Result.success(
                new ActionTimeline(
                        move, nextTick, nextPhase, nextResourceState, nextResources, events));
    }

    public Result<ActionTimeline, ActionTimelineErrorCode> cancel(String reason) {
        Objects.requireNonNull(reason, "reason");
        if (phase.terminal()) {
            return Result.failure(
                    ActionTimelineErrorCode.ACTION_ALREADY_FINISHED,
                    "Terminal action timeline cannot be cancelled.");
        }
        ArrayList<ActionTraceEvent> events = new ArrayList<>(trace);
        CombatResources nextResources = resources;
        ResourceCommitState nextResourceState = resourceState;
        if (resourceState == ResourceCommitState.RESERVED) {
            nextResources = resources.cancelBeforeCommit(move.costs());
            nextResourceState = ResourceCommitState.RELEASED;
            if (move.costs().setupStamina() > 0) {
                events.add(
                        new ActionTraceEvent(
                                tick,
                                ActionTraceEventType.SETUP_COST_COMMITTED,
                                "stamina=" + move.costs().setupStamina()));
            }
        }
        events.add(new ActionTraceEvent(tick, ActionTraceEventType.ACTION_CANCELLED, reason));
        return Result.success(
                new ActionTimeline(
                        move,
                        tick,
                        ActionPhase.CANCELLED,
                        nextResourceState,
                        nextResources,
                        events));
    }

    public boolean chainWindowOpen(String branch) {
        Objects.requireNonNull(branch, "branch");
        return move.cancels().chainWindows().stream()
                .anyMatch(
                        window ->
                                window.branch().equals(branch)
                                        && tick >= window.fromTick()
                                        && tick <= window.toTick());
    }

    public boolean dodgeCancelOpen() {
        return tick >= move.cancels().dodgeFromTick() && !phase.terminal();
    }

    private static ActionPhase phaseAt(MoveDefinition move, int tick) {
        if (tick < move.phases().windupTicks()) {
            return ActionPhase.WINDUP;
        }
        if (tick < move.phases().windupTicks() + move.phases().activeTicks()) {
            return ActionPhase.ACTIVE;
        }
        return ActionPhase.RECOVERY;
    }

    private static void appendHitboxes(
            MoveDefinition move, int tick, List<ActionTraceEvent> events) {
        for (int index = 0; index < move.hitboxes().size(); index++) {
            MoveDefinition.Hitbox hitbox = move.hitboxes().get(index);
            if (hitbox.tick() == tick) {
                events.add(
                        new ActionTraceEvent(
                                tick,
                                ActionTraceEventType.HITBOX_OPENED,
                                index + ":" + hitbox.hitGroup() + ":" + hitbox.shape()));
            }
        }
    }

    private static String costDetail(MoveDefinition.ResourceCost cost) {
        return "stamina=" + cost.stamina() + ",mana=" + cost.mana() + ",health=" + cost.health();
    }
}
