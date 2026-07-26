package com.branz.mmorpg.quest.core;

import com.branz.mmorpg.quest.api.ActionDefinition;
import com.branz.mmorpg.quest.api.ConditionDefinition;
import com.branz.mmorpg.quest.api.DialogueChoice;
import com.branz.mmorpg.quest.api.DialogueDefinition;
import com.branz.mmorpg.quest.api.DialogueNode;
import com.branz.mmorpg.quest.api.DialogueSession;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DialogueEngine {
    private static final int MAXIMUM_AUTOMATIC_STEPS = 128;
    public interface Conditions {
        boolean all(UUID playerId, List<ConditionDefinition> conditions);
    }
    public interface Actions {
        void run(UUID playerId, UUID sessionId, long sequence,
                 List<ActionDefinition> actions);
    }
    public record AdvanceResult(Status status, DialogueSession session, String detail) {
        public enum Status { ADVANCED, WAITING, STALE_INPUT, CHOICE_DISABLED, COMPLETE }
    }

    public void validate(DialogueDefinition definition) {
        definition.nodes().forEach((id, node) -> {
            if (!id.equals(node.id())) throw new IllegalArgumentException("dialogue node key mismatch");
            node.next().ifPresent(next -> requireNode(definition, next));
            node.jumpTarget().ifPresent(next -> requireNode(definition, next));
            HashSet<String> choices = new HashSet<>();
            node.choices().forEach(choice -> {
                if (!choices.add(choice.id())) {
                    throw new IllegalArgumentException("duplicate choice " + choice.id());
                }
                requireNode(definition, choice.next());
            });
        });
        HashSet<String> reachable = new HashSet<>();
        walk(definition, definition.startNode(), reachable);
        if (reachable.size() != definition.nodes().size()) {
            throw new IllegalArgumentException("dialogue contains unreachable nodes");
        }
    }

    public DialogueSession start(
            DialogueDefinition definition, UUID playerId,
            long contentRevision, Instant now, Conditions conditions, Actions actions) {
        validate(definition);
        DialogueSession initial = new DialogueSession(UUID.randomUUID(), playerId,
                definition.id(), definition.version(), definition.startNode(),
                java.util.Set.of(), java.util.Map.of(), contentRevision, 0,
                DialogueSession.State.ACTIVE, now, now);
        return resolve(definition, initial, conditions, actions, now);
    }

    public AdvanceResult advance(
            DialogueDefinition definition, DialogueSession session,
            long expectedSequence, Optional<String> choiceId,
            Conditions conditions, Actions actions, Instant now) {
        if (expectedSequence != session.sequence()) {
            return new AdvanceResult(AdvanceResult.Status.STALE_INPUT, session,
                    "expected sequence " + session.sequence());
        }
        if (session.state() != DialogueSession.State.ACTIVE) {
            return new AdvanceResult(session.state() == DialogueSession.State.COMPLETE
                    ? AdvanceResult.Status.COMPLETE : AdvanceResult.Status.WAITING,
                    session, "session is not active");
        }
        DialogueNode current = requireNode(definition, session.currentNode());
        String next;
        HashMap<String, String> selected = new HashMap<>(session.selectedChoices());
        if (current.type() == DialogueNode.Type.CHOICE) {
            DialogueChoice choice = current.choices().stream()
                    .filter(value -> choiceId.isPresent()
                            && value.id().equals(choiceId.orElseThrow()))
                    .findFirst().orElseThrow(() ->
                            new IllegalArgumentException("unknown dialogue choice"));
            if (!conditions.all(session.playerId(), choice.conditions())) {
                return new AdvanceResult(AdvanceResult.Status.CHOICE_DISABLED,
                        session, choice.disabledReasonKey());
            }
            actions.run(session.playerId(), session.sessionId(),
                    session.sequence(), choice.actions());
            selected.put(current.id(), choice.id());
            next = choice.next();
        } else if (current.type() == DialogueNode.Type.WAIT
                && now.isBefore(session.lastActiveAt()
                .plusMillis(current.durationMillis()))) {
            return new AdvanceResult(AdvanceResult.Status.WAITING, session, "wait not elapsed");
        } else if (current.advanceMode() == DialogueNode.AdvanceMode.EXTERNAL_SIGNAL
                && choiceId.isEmpty()) {
            return new AdvanceResult(AdvanceResult.Status.WAITING, session,
                    "external signal required");
        } else {
            next = current.next().orElse(current.jumpTarget().orElse(current.id()));
        }
        DialogueSession moved = copy(session, next, selected,
                session.sequence() + 1, DialogueSession.State.ACTIVE, now);
        DialogueSession resolved = resolve(definition, moved, conditions, actions, now);
        return new AdvanceResult(resolved.state() == DialogueSession.State.COMPLETE
                ? AdvanceResult.Status.COMPLETE : AdvanceResult.Status.ADVANCED,
                resolved, "");
    }

    public DialogueSession interrupt(
            DialogueSession session, boolean pause, Instant now) {
        return copy(session, session.currentNode(), session.selectedChoices(),
                session.sequence() + 1, pause
                        ? DialogueSession.State.PAUSED : DialogueSession.State.CANCELLED, now);
    }

    public DialogueSession resume(DialogueSession session, Instant now) {
        if (session.state() != DialogueSession.State.PAUSED) {
            throw new IllegalStateException("dialogue is not paused");
        }
        return copy(session, session.currentNode(), session.selectedChoices(),
                session.sequence() + 1, DialogueSession.State.ACTIVE, now);
    }

    private DialogueSession resolve(
            DialogueDefinition definition, DialogueSession start,
            Conditions conditions, Actions actions, Instant now) {
        DialogueSession current = start;
        for (int step = 0; step < MAXIMUM_AUTOMATIC_STEPS; step++) {
            DialogueNode node = requireNode(definition, current.currentNode());
            HashSet<String> visited = new HashSet<>(current.visitedNodes());
            visited.add(node.id());
            current = new DialogueSession(current.sessionId(), current.playerId(),
                    current.dialogueId(), current.definitionVersion(), current.currentNode(),
                    visited, current.selectedChoices(), current.contentRevision(),
                    current.sequence(), current.state(), current.startedAt(), now);
            if (node.type() == DialogueNode.Type.END) {
                return copy(current, node.id(), current.selectedChoices(),
                        current.sequence(), DialogueSession.State.COMPLETE, now);
            }
            if (node.type() == DialogueNode.Type.LINE
                    || node.type() == DialogueNode.Type.CHOICE
                    || node.type() == DialogueNode.Type.WAIT) return current;
            if (node.type() == DialogueNode.Type.ACTION) {
                actions.run(current.playerId(), current.sessionId(),
                        current.sequence(), node.actions());
            }
            String next;
            if (node.type() == DialogueNode.Type.CONDITION) {
                boolean passed = conditions.all(current.playerId(), node.conditions());
                next = passed ? node.next().orElseThrow()
                        : node.jumpTarget().orElseThrow();
            } else {
                next = node.jumpTarget().or(() -> node.next()).orElseThrow();
            }
            current = copy(current, next, current.selectedChoices(),
                    current.sequence() + 1, DialogueSession.State.ACTIVE, now);
        }
        throw new IllegalStateException("dialogue automatic cycle exceeded bound");
    }

    private static DialogueSession copy(
            DialogueSession source, String node, java.util.Map<String, String> selected,
            long sequence, DialogueSession.State state, Instant now) {
        return new DialogueSession(source.sessionId(), source.playerId(),
                source.dialogueId(), source.definitionVersion(), node,
                source.visitedNodes(), selected, source.contentRevision(),
                sequence, state, source.startedAt(), now);
    }

    private static DialogueNode requireNode(DialogueDefinition definition, String id) {
        DialogueNode node = definition.nodes().get(id);
        if (node == null) throw new IllegalArgumentException("unknown dialogue node " + id);
        return node;
    }

    private static void walk(
            DialogueDefinition definition, String id, HashSet<String> visited) {
        if (!visited.add(id)) return;
        DialogueNode node = requireNode(definition, id);
        node.next().ifPresent(next -> walk(definition, next, visited));
        node.jumpTarget().ifPresent(next -> walk(definition, next, visited));
        node.choices().forEach(choice -> walk(definition, choice.next(), visited));
    }
}
