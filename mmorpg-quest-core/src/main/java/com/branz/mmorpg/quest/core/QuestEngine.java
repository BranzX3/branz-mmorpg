package com.branz.mmorpg.quest.core;

import com.branz.mmorpg.quest.api.ActionDefinition;
import com.branz.mmorpg.quest.api.ObjectiveProgress;
import com.branz.mmorpg.quest.api.PendingQuestOperation;
import com.branz.mmorpg.quest.api.QuestDefinition;
import com.branz.mmorpg.quest.api.QuestEvent;
import com.branz.mmorpg.quest.api.QuestGamePort;
import com.branz.mmorpg.quest.api.QuestProgress;
import com.branz.mmorpg.quest.api.QuestStageDefinition;
import com.branz.mmorpg.quest.api.QuestState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QuestEngine {
    private final ObjectiveEngine objectives = new ObjectiveEngine();

    public QuestProgress start(
            UUID playerId, QuestDefinition definition,
            Optional<QuestProgress> previous, Instant now) {
        if (previous.isPresent() && previous.orElseThrow().state() == QuestState.COMPLETED
                && definition.repeatPolicy() == QuestDefinition.RepeatPolicy.NEVER) {
            throw new IllegalStateException("non-repeatable quest is already completed");
        }
        UUID occurrence = UUID.randomUUID();
        return new QuestProgress(playerId, definition.id(), definition.version(), 0,
                QuestState.ACTIVE, definition.startStage(), occurrence,
                initial(definition.stages().get(definition.startStage())),
                Map.of(), now, now, Optional.empty());
    }

    public QuestTransition event(
            QuestProgress before, QuestDefinition definition,
            QuestEvent event, QuestGamePort game, Instant now) {
        if (before.state() != QuestState.ACTIVE) {
            return new QuestTransition(false, before, List.of());
        }
        QuestStageDefinition stage = requireStage(definition, before.stageId());
        HashMap<String, ObjectiveProgress> changed = new HashMap<>(before.objectives());
        boolean any = false;
        for (var objective : stage.objectives()) {
            ObjectiveProgress old = changed.get(objective.id());
            ObjectiveProgress next =
                    objectives.reduce(before.playerId(), objective, old, event, game);
            if (!next.equals(old)) {
                changed.put(objective.id(), next);
                any = true;
            }
        }
        if (!any) return new QuestTransition(false, before, List.of());
        QuestProgress progressed = copy(before, before.state(), before.stageId(),
                changed, before.flags(), now, before.completedAt());
        if (!stageComplete(stage, changed)) {
            return new QuestTransition(true, progressed, List.of());
        }
        return completeStage(progressed, definition, stage, event, game, now, 0);
    }

    /** Reconciles possession/mastery objectives against canonical game state. */
    public QuestTransition refreshQueries(
            QuestProgress before, QuestDefinition definition,
            QuestGamePort game, Instant now) {
        if (before.state() != QuestState.ACTIVE) {
            return new QuestTransition(false, before, List.of());
        }
        QuestStageDefinition stage = requireStage(definition, before.stageId());
        HashMap<String, ObjectiveProgress> changed = new HashMap<>(before.objectives());
        QuestEvent queryEvent = new QuestEvent(UUID.randomUUID(),
                QuestEvent.Type.TIMER_ELAPSED, before.playerId(), Optional.empty(),
                0, "query", java.util.Set.of(), false, Map.of(), now);
        boolean any = false;
        for (var objective : stage.objectives()) {
            if (!objectives.query(objective)) continue;
            ObjectiveProgress old = changed.get(objective.id());
            ObjectiveProgress next = objectives.reduce(
                    before.playerId(), objective, old, queryEvent, game);
            if (!next.equals(old)) {
                changed.put(objective.id(), next);
                any = true;
            }
        }
        if (!any) return new QuestTransition(false, before, List.of());
        QuestProgress progressed = copy(before, before.state(), before.stageId(),
                changed, before.flags(), now, before.completedAt());
        if (!stageComplete(stage, changed)) {
            return new QuestTransition(true, progressed, List.of());
        }
        return completeStage(progressed, definition, stage, queryEvent, game, now, 0);
    }

    public QuestTransition setStage(
            QuestProgress before, QuestDefinition definition, String stageId,
            QuestGamePort game, Instant now) {
        if (before.definitionVersion() != definition.version()) {
            throw new IllegalStateException("quest must be migrated before stage repair");
        }
        QuestStageDefinition stage = requireStage(definition, stageId);
        QuestProgress moved = copy(before, QuestState.ACTIVE, stageId,
                initial(stage), before.flags(), now, Optional.empty());
        QuestTransition refreshed = refreshQueries(moved, definition, game, now);
        ArrayList<PendingQuestOperation> operations =
                operations(moved, stage.activationActions(), now);
        if (refreshed.changed()) operations.addAll(refreshed.operations());
        return new QuestTransition(true,
                refreshed.changed() ? refreshed.progress() : moved, operations);
    }

    public QuestTransition setObjective(
            QuestProgress before, QuestDefinition definition,
            String objectiveId, long value, QuestGamePort game, Instant now) {
        QuestStageDefinition stage = requireStage(definition, before.stageId());
        var objective = stage.objectives().stream()
                .filter(candidate -> candidate.id().equals(objectiveId))
                .findFirst().orElseThrow(() ->
                        new IllegalArgumentException("unknown active objective " + objectiveId));
        if (value < 0 || value > objective.targetAmount()) {
            throw new IllegalArgumentException("objective value outside [0,target]");
        }
        HashMap<String, ObjectiveProgress> values = new HashMap<>(before.objectives());
        ObjectiveProgress old = values.get(objectiveId);
        values.put(objectiveId, new ObjectiveProgress(
                value, objective.targetAmount(), old.data()));
        QuestProgress changed = copy(before, QuestState.ACTIVE, before.stageId(),
                values, before.flags(), now, Optional.empty());
        if (!stageComplete(stage, values)) {
            return new QuestTransition(true, changed, List.of());
        }
        QuestEvent synthetic = new QuestEvent(UUID.randomUUID(),
                QuestEvent.Type.TIMER_ELAPSED, before.playerId(), Optional.empty(),
                0, "admin_repair", java.util.Set.of(), false, Map.of(), now);
        return completeStage(changed, definition, stage, synthetic, game, now, 0);
    }

    private QuestTransition completeStage(
            QuestProgress progressed, QuestDefinition definition,
            QuestStageDefinition stage, QuestEvent event,
            QuestGamePort game, Instant now, int depth) {
        if (depth >= definition.stages().size()) {
            throw new IllegalStateException("quest stage cycle exceeded bound");
        }
        ArrayList<PendingQuestOperation> operations =
                operations(progressed, stage.completionActions(), now);
        if (stage.nextStage().isPresent()) {
            String nextId = stage.nextStage().orElseThrow();
            QuestStageDefinition next = requireStage(definition, nextId);
            Map<String, ObjectiveProgress> initial = initial(next);
            HashMap<String, ObjectiveProgress> queried = new HashMap<>(initial);
            for (var objective : next.objectives()) {
                if (!objectives.query(objective)) continue;
                ObjectiveProgress before = queried.get(objective.id());
                queried.put(objective.id(), objectives.reduce(progressed.playerId(),
                        objective, before, event, game));
            }
            QuestProgress advanced = copy(progressed, QuestState.ACTIVE, nextId,
                    queried, progressed.flags(), now, Optional.empty());
            operations.addAll(operations(advanced, next.activationActions(), now));
            if (stageComplete(next, queried)) {
                QuestTransition settled = completeStage(
                        advanced, definition, next, event, game, now, depth + 1);
                operations.addAll(settled.operations());
                return new QuestTransition(true, settled.progress(), operations);
            }
            return new QuestTransition(true, advanced, operations);
        }
        QuestProgress ready = copy(progressed, QuestState.READY_TO_TURN_IN,
                progressed.stageId(), progressed.objectives(), progressed.flags(),
                now, Optional.empty());
        return new QuestTransition(true, ready, operations);
    }

    public QuestTransition turnIn(
            QuestProgress before, QuestDefinition definition, Instant now) {
        if (before.state() != QuestState.READY_TO_TURN_IN) {
            throw new IllegalStateException("quest is not ready to turn in");
        }
        QuestProgress completing = copy(before, QuestState.COMPLETING,
                before.stageId(), before.objectives(), before.flags(), now, Optional.empty());
        return new QuestTransition(true, completing,
                operations(completing, definition.rewards(), now));
    }

    public QuestProgress rewardsComplete(QuestProgress before, Instant now) {
        if (before.state() != QuestState.COMPLETING) {
            throw new IllegalStateException("quest is not completing");
        }
        return copy(before, QuestState.COMPLETED, before.stageId(), before.objectives(),
                before.flags(), now, Optional.of(now));
    }

    public QuestProgress abandon(QuestProgress before, Instant now) {
        if (before.state() != QuestState.ACTIVE
                && before.state() != QuestState.READY_TO_TURN_IN) {
            throw new IllegalStateException("quest cannot be abandoned");
        }
        return copy(before, QuestState.ABANDONED, before.stageId(), before.objectives(),
                before.flags(), now, Optional.empty());
    }

    private Map<String, ObjectiveProgress> initial(QuestStageDefinition stage) {
        HashMap<String, ObjectiveProgress> result = new HashMap<>();
        stage.objectives().forEach(objective ->
                result.put(objective.id(), objectives.initial(objective)));
        return Map.copyOf(result);
    }

    private static boolean stageComplete(
            QuestStageDefinition stage, Map<String, ObjectiveProgress> values) {
        long complete = values.values().stream().filter(ObjectiveProgress::complete).count();
        return switch (stage.completionPolicy()) {
            case ALL -> complete == values.size();
            case ANY -> complete > 0;
            case COUNT -> complete >= stage.completionCount();
            case EXPRESSION -> complete == values.size();
        };
    }

    private static ArrayList<PendingQuestOperation> operations(
            QuestProgress progress, List<ActionDefinition> actions, Instant now) {
        ArrayList<PendingQuestOperation> result = new ArrayList<>();
        for (ActionDefinition action : actions) {
            String id = "quest:" + progress.occurrenceId() + ':' + progress.stageId()
                    + ':' + action.id();
            HashMap<String, String> payload = new HashMap<>(action.values());
            action.numbers().forEach((key, value) -> payload.put(key, value.toString()));
            payload.put("required", Boolean.toString(action.required()));
            result.add(new PendingQuestOperation(id, progress.playerId(), progress.questId(),
                    action.type(), payload, PendingQuestOperation.State.PENDING,
                    0, now, ""));
        }
        return result;
    }

    private static QuestStageDefinition requireStage(
            QuestDefinition definition, String stageId) {
        QuestStageDefinition stage = definition.stages().get(stageId);
        if (stage == null) throw new IllegalStateException("unknown active stage " + stageId);
        return stage;
    }

    private static QuestProgress copy(
            QuestProgress source, QuestState state, String stage,
            Map<String, ObjectiveProgress> objectives, Map<String, String> flags,
            Instant now, Optional<Instant> completed) {
        return new QuestProgress(source.playerId(), source.questId(),
                source.definitionVersion(), source.revision() + 1, state, stage,
                source.occurrenceId(), objectives, flags,
                source.startedAt(), now, completed);
    }
}
