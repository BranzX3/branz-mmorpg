package com.branz.mmorpg.quest.core;

import com.branz.mmorpg.quest.api.CutsceneAction;
import com.branz.mmorpg.quest.api.CutsceneDefinition;
import com.branz.mmorpg.quest.api.CutsceneSession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CutsceneEngine {
    public record Step(CutsceneSession session, List<CutsceneAction> actions) {
        public Step { actions = List.copyOf(actions); }
    }

    public Step prepare(
            CutsceneDefinition definition, Set<UUID> participants,
            long monotonicNanos, Instant now) {
        validate(definition);
        CutsceneSession session = new CutsceneSession(
                UUID.randomUUID(), definition.id(), definition.version(), participants,
                CutsceneSession.State.PREPARING, 0, monotonicNanos, 0,
                Set.of(), Set.of(), false, false, false, now, now);
        return apply(session, definition.setup(), CutsceneSession.State.PLAYING,
                monotonicNanos, now);
    }

    public Step advance(CutsceneDefinition definition, CutsceneSession before,
                        long monotonicNanos, Instant now) {
        if (before.state() != CutsceneSession.State.PLAYING) {
            return new Step(before, List.of());
        }
        long delta = Math.max(0, monotonicNanos - before.lastMonotonicNanos()) / 1_000_000;
        long elapsed = Math.addExact(before.elapsedMillis(), delta);
        ArrayList<CutsceneAction> due = new ArrayList<>();
        int index = before.nextActionIndex();
        while (index < definition.timeline().size()
                && definition.timeline().get(index).atMillis() <= elapsed) {
            CutsceneAction action = definition.timeline().get(index++);
            if (!before.appliedActionIds().contains(action.id())) due.add(action);
        }
        CutsceneSession progressed = copy(before, CutsceneSession.State.PLAYING,
                elapsed, monotonicNanos, index, union(before.appliedActionIds(), due),
                now, before.cameraAttached(), before.inputFrozen(), before.invulnerable());
        if (index < definition.timeline().size()) return new Step(progressed, due);
        Step finalStep = apply(progressed, definition.finalState(),
                CutsceneSession.State.COMPLETING, monotonicNanos, now);
        ArrayList<CutsceneAction> all = new ArrayList<>(due);
        all.addAll(finalStep.actions());
        return new Step(finalStep.session(), all);
    }

    public Step skip(CutsceneDefinition definition, CutsceneSession before,
                     long monotonicNanos, Instant now) {
        if (definition.skipPolicy() == CutsceneDefinition.SkipPolicy.NEVER) {
            throw new IllegalStateException("cutscene is not skippable");
        }
        if (before.state() == CutsceneSession.State.COMPLETE) {
            return new Step(before, List.of());
        }
        return apply(copy(before, CutsceneSession.State.SKIPPING,
                        before.elapsedMillis(), monotonicNanos, before.nextActionIndex(),
                        before.appliedActionIds(), now, before.cameraAttached(),
                        before.inputFrozen(), before.invulnerable()),
                definition.skipState(), CutsceneSession.State.COMPLETING,
                monotonicNanos, now);
    }

    public Step beginCleanup(
            CutsceneDefinition definition, CutsceneSession before,
            long monotonicNanos, Instant now) {
        if (before.state() == CutsceneSession.State.CLEANING) {
            return new Step(before, definition.cleanup().stream()
                    .filter(action -> !before.appliedActionIds().contains(action.id())).toList());
        }
        if (before.state() != CutsceneSession.State.COMPLETING
                && before.state() != CutsceneSession.State.FAILED) {
            throw new IllegalStateException("cutscene cannot clean from " + before.state());
        }
        return apply(before, definition.cleanup(), CutsceneSession.State.CLEANING,
                monotonicNanos, now);
    }

    public CutsceneSession cleanupComplete(CutsceneSession before, Instant now) {
        if (before.state() == CutsceneSession.State.COMPLETE) return before;
        if (before.state() != CutsceneSession.State.CLEANING) {
            throw new IllegalStateException("cutscene is not cleaning");
        }
        return new CutsceneSession(before.sessionId(), before.cutsceneId(),
                before.definitionVersion(), before.participantSnapshot(),
                CutsceneSession.State.COMPLETE, before.elapsedMillis(),
                before.lastMonotonicNanos(), before.nextActionIndex(),
                before.appliedActionIds(), Set.of(), false, false, false,
                before.startedAt(), now);
    }

    public Step disconnect(CutsceneDefinition definition, CutsceneSession before,
                           long monotonicNanos, Instant now) {
        return switch (definition.disconnectRecovery()) {
            case PAUSE_RESUME -> new Step(copy(before, CutsceneSession.State.PAUSED,
                    before.elapsedMillis(), monotonicNanos, before.nextActionIndex(),
                    before.appliedActionIds(), now, before.cameraAttached(),
                    before.inputFrozen(), before.invulnerable()), List.of());
            case APPLY_SKIP_STATE -> skip(definition, before, monotonicNanos, now);
            case APPLY_FINAL_STATE -> apply(before, definition.finalState(),
                    CutsceneSession.State.COMPLETING, monotonicNanos, now);
            case FAIL -> new Step(copy(before, CutsceneSession.State.FAILED,
                    before.elapsedMillis(), monotonicNanos, before.nextActionIndex(),
                    before.appliedActionIds(), now, before.cameraAttached(),
                    before.inputFrozen(), before.invulnerable()), List.of());
        };
    }

    public CutsceneSession resume(
            CutsceneSession before, long monotonicNanos, Instant now) {
        if (before.state() != CutsceneSession.State.PAUSED) {
            throw new IllegalStateException("cutscene is not paused");
        }
        return copy(before, CutsceneSession.State.PLAYING, before.elapsedMillis(),
                monotonicNanos, before.nextActionIndex(), before.appliedActionIds(),
                now, before.cameraAttached(), before.inputFrozen(), before.invulnerable());
    }

    public void validate(CutsceneDefinition definition) {
        HashSet<String> ids = new HashSet<>();
        java.util.stream.Stream.of(definition.setup(), definition.timeline(),
                definition.finalState(), definition.skipState(), definition.cleanup())
                .flatMap(List::stream).forEach(action -> {
                    if (!ids.add(action.id())) {
                        throw new IllegalArgumentException(
                                "duplicate cutscene action " + action.id());
                    }
                });
        if (definition.cleanup().stream().noneMatch(action ->
                action.type() == CutsceneAction.Type.CAMERA_RESTORE)
                || definition.cleanup().stream().noneMatch(action ->
                action.type() == CutsceneAction.Type.FREEZE_INPUT)) {
            throw new IllegalArgumentException(
                    "cleanup must restore camera and input policy");
        }
    }

    private Step apply(CutsceneSession before, List<CutsceneAction> actions,
                       CutsceneSession.State state, long monotonic, Instant now) {
        List<CutsceneAction> pending = actions.stream()
                .filter(action -> !before.appliedActionIds().contains(action.id()))
                .sorted(CutsceneAction.ORDER).toList();
        CutsceneSession after = copy(before, state, before.elapsedMillis(), monotonic,
                before.nextActionIndex(), union(before.appliedActionIds(), pending),
                now, flags(before.cameraAttached(), pending, "camera"),
                flags(before.inputFrozen(), pending, "freeze"),
                flags(before.invulnerable(), pending, "invulnerable"));
        return new Step(after, pending);
    }

    private static boolean flags(
            boolean current, List<CutsceneAction> actions, String kind) {
        boolean value = current;
        for (CutsceneAction action : actions) {
            if (kind.equals("camera") && action.type() == CutsceneAction.Type.CAMERA_CUT) value = true;
            if (kind.equals("camera") && action.type() == CutsceneAction.Type.CAMERA_RESTORE) value = false;
            if (kind.equals("freeze") && action.type() == CutsceneAction.Type.FREEZE_INPUT) {
                value = Boolean.parseBoolean(action.values().getOrDefault("enabled", "false"));
            }
            if (kind.equals("invulnerable")
                    && action.type() == CutsceneAction.Type.INVULNERABILITY) {
                value = Boolean.parseBoolean(action.values().getOrDefault("enabled", "false"));
            }
        }
        return value;
    }

    private static Set<String> union(
            Set<String> before, List<CutsceneAction> actions) {
        HashSet<String> result = new HashSet<>(before);
        actions.forEach(action -> result.add(action.id()));
        return Set.copyOf(result);
    }

    private static CutsceneSession copy(
            CutsceneSession source, CutsceneSession.State state,
            long elapsed, long monotonic, int next, Set<String> applied,
            Instant now, boolean camera, boolean frozen, boolean invulnerable) {
        return new CutsceneSession(source.sessionId(), source.cutsceneId(),
                source.definitionVersion(), source.participantSnapshot(), state,
                elapsed, monotonic, next, applied, source.actorIds(),
                camera, frozen, invulnerable, source.startedAt(), now);
    }
}
