package com.branz.mmorpg.quest.core;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.quest.api.ConditionDefinition;
import com.branz.mmorpg.quest.api.PendingQuestOperation;
import com.branz.mmorpg.quest.api.QuestCatalog;
import com.branz.mmorpg.quest.api.QuestCommit;
import com.branz.mmorpg.quest.api.QuestDefinition;
import com.branz.mmorpg.quest.api.QuestEvent;
import com.branz.mmorpg.quest.api.QuestGamePort;
import com.branz.mmorpg.quest.api.QuestProgress;
import com.branz.mmorpg.quest.api.QuestProgressStore;
import com.branz.mmorpg.quest.api.QuestMigrationDefinition;
import com.branz.mmorpg.quest.api.QuestService;
import com.branz.mmorpg.quest.api.QuestState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class DefaultQuestService implements QuestService {
    private final QuestProgressStore store;
    private final QuestGamePort game;
    private final Supplier<QuestCatalog> catalog;
    private final GameClock clock;
    private final QuestEngine engine = new QuestEngine();
    private final ConditionEngine conditions = new ConditionEngine();
    private final MigrationLookup migrations;
    private final QuestMigrationEngine migrationEngine = new QuestMigrationEngine();
    private volatile ObjectiveIndex objectiveIndex;

    public DefaultQuestService(QuestProgressStore store, QuestGamePort game,
                               Supplier<QuestCatalog> catalog, GameClock clock) {
        this(store, game, catalog, clock, (id, from, to) -> Optional.empty());
    }

    public DefaultQuestService(
            QuestProgressStore store, QuestGamePort game,
            Supplier<QuestCatalog> catalog, GameClock clock,
            MigrationLookup migrations) {
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.game = java.util.Objects.requireNonNull(game, "game");
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.migrations = java.util.Objects.requireNonNull(migrations, "migrations");
    }

    @Override public QuestProgress start(UUID playerId, ContentId questId) {
        QuestDefinition definition = definition(questId);
        HashMap<ContentId, QuestProgress> current = new HashMap<>();
        store.active(playerId).forEach(value -> current.put(value.questId(), value));
        for (ConditionDefinition requirement : definition.requirements()) {
            ConditionEngine.Result result = conditions.evaluate(
                    requirement, playerId, current, game, clock.now());
            if (result != ConditionEngine.Result.TRUE) {
                throw new IllegalStateException("quest requirements are not satisfied: " + result);
            }
        }
        Optional<QuestProgress> previous = store.load(playerId, questId);
        QuestProgress started = engine.start(playerId, definition, previous, clock.now());
        ArrayList<PendingQuestOperation> operations = new ArrayList<>(
                operationBuilder(started, definition.stages()
                        .get(definition.startStage()).activationActions()));
        QuestTransition refreshed =
                engine.refreshQueries(started, definition, game, clock.now());
        if (refreshed.changed()) operations.addAll(refreshed.operations());
        Materialized materialized = materialize(
                refreshed.changed() ? refreshed.progress() : started, operations);
        return store.insert(materialized.progress(), materialized.operations()).progress();
    }

    @Override public Collection<QuestProgress> process(QuestEvent event) {
        QuestCatalog currentCatalog = catalog.get();
        ObjectiveIndex index = objectiveIndex;
        if (index == null || index.revision() != currentCatalog.revision()) {
            index = ObjectiveIndex.build(currentCatalog);
            objectiveIndex = index;
        }
        Set<ContentId> candidates = index.candidates(event.type());
        if (candidates.isEmpty()) return List.of();
        HashSet<UUID> recipients = new HashSet<>(event.partyInRangeSnapshot());
        recipients.add(event.playerId());
        ArrayList<QuestProgress> changed = new ArrayList<>();
        for (UUID playerId : recipients) {
            for (QuestProgress progress : store.active(playerId, candidates)) {
                if (store.hasIncompleteRequiredOperations(
                        playerId, progress.questId())) {
                    continue;
                }
                QuestDefinition definition =
                        currentCatalog.find(progress.questId()).orElse(null);
                if (definition == null) {
                    continue;
                }
                if (definition.version() != progress.definitionVersion()) {
                    if (definition.migrationPolicy() == QuestDefinition.MigrationPolicy.SAFE) {
                        QuestMigrationDefinition identity = new QuestMigrationDefinition(
                                definition.id(), progress.definitionVersion(),
                                definition.version(), Map.of(), Map.of());
                        QuestProgress migrated = migrationEngine.migrate(
                                progress, definition, identity, clock.now());
                        progress = store.migrate(progress, migrated,
                                new UUID(0L, 0L), "automatic safe definition migration");
                    } else {
                        QuestProgress required =
                                migrationEngine.markRequired(progress, clock.now());
                        store.commit(progress, required, UUID.nameUUIDFromBytes(
                                ("migration-required:" + progress.occurrenceId() + ":"
                                        + definition.version()).getBytes(
                                        java.nio.charset.StandardCharsets.UTF_8)), List.of());
                        continue;
                    }
                }
                QuestTransition transition =
                        engine.event(progress, definition, event, game, clock.now());
                if (!transition.changed()) continue;
                Materialized materialized =
                        materialize(transition.progress(), transition.operations());
                QuestCommit commit = store.commit(progress, materialized.progress(),
                        event.eventId(), materialized.operations());
                if (commit.applied()) changed.add(commit.progress());
            }
        }
        return List.copyOf(changed);
    }

    @Override public QuestProgress turnIn(UUID playerId, ContentId questId) {
        if (store.hasIncompleteRequiredOperations(playerId, questId)) {
            throw new IllegalStateException(
                    "required quest actions are still pending");
        }
        QuestProgress before = require(playerId, questId);
        QuestTransition transition =
                engine.turnIn(before, definition(questId), clock.now());
        Materialized materialized =
                materialize(transition.progress(), transition.operations());
        return store.commit(before, materialized.progress(), UUID.randomUUID(),
                materialized.operations()).progress();
    }

    @Override public QuestProgress abandon(UUID playerId, ContentId questId) {
        QuestProgress before = require(playerId, questId);
        QuestProgress after = engine.abandon(before, clock.now());
        return store.commit(before, after, UUID.randomUUID(), List.of()).progress();
    }

    @Override public Optional<QuestProgress> progress(UUID playerId, ContentId questId) {
        return store.load(playerId, questId);
    }

    @Override public Collection<QuestProgress> active(UUID playerId) {
        return store.active(playerId);
    }

    @Override public int retryPending(int limit) {
        int completed = 0;
        for (PendingQuestOperation operation : store.pending(clock.now(), limit)) {
            if (store.hasEarlierIncompleteRequiredOperation(operation)) continue;
            try {
                QuestGamePort.ActionResult result = game.execute(operation);
                boolean required = Boolean.parseBoolean(
                        operation.payload().getOrDefault("required", "true"));
                if (result.status() == QuestGamePort.ActionResult.Status.APPLIED
                        || result.status() == QuestGamePort.ActionResult.Status.ALREADY_APPLIED
                        || !required) {
                    store.completeOperation(operation.operationId());
                    completed++;
                    finalizeIfReady(operation.playerId(), operation.questId());
                } else {
                    store.failOperation(operation.operationId(), result.detail(),
                            retryAt(operation.attempts()));
                }
            } catch (RuntimeException failure) {
                store.failOperation(operation.operationId(), failure.getMessage(),
                        retryAt(operation.attempts()));
            }
        }
        return completed;
    }

    @Override public QuestProgress migrate(
            UUID playerId, ContentId questId, UUID actorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("migration reason is required");
        }
        QuestProgress before = require(playerId, questId);
        QuestDefinition target = definition(questId);
        if (before.definitionVersion() == target.version()) return before;
        QuestMigrationDefinition mapping = migrations.find(
                questId, before.definitionVersion(), target.version()).orElseGet(() -> {
            if (target.migrationPolicy() == QuestDefinition.MigrationPolicy.SAFE) {
                return new QuestMigrationDefinition(questId,
                        before.definitionVersion(), target.version(), Map.of(), Map.of());
            }
            throw new IllegalStateException("quest migration mapping is required");
        });
        QuestProgress after =
                migrationEngine.migrate(before, target, mapping, clock.now());
        return store.migrate(before, after, actorId, reason);
    }

    @Override public QuestProgress setStage(
            UUID playerId, ContentId questId, String stageId,
            UUID actorId, String reason) {
        requireReason(reason);
        QuestProgress before = require(playerId, questId);
        QuestTransition transition = engine.setStage(
                before, definition(questId), stageId, game, clock.now());
        Materialized materialized =
                materialize(transition.progress(), transition.operations());
        return store.repair(before, materialized.progress(),
                materialized.operations(), actorId, "quest_stage", reason);
    }

    @Override public QuestProgress setObjective(
            UUID playerId, ContentId questId, String objectiveId, long value,
            UUID actorId, String reason) {
        requireReason(reason);
        QuestProgress before = require(playerId, questId);
        QuestTransition transition = engine.setObjective(
                before, definition(questId), objectiveId, value, game, clock.now());
        Materialized materialized =
                materialize(transition.progress(), transition.operations());
        return store.repair(before, materialized.progress(),
                materialized.operations(), actorId, "quest_objective", reason);
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("admin repair reason is required");
        }
    }

    private void finalizeIfReady(UUID playerId, ContentId questId) {
        if (store.hasIncompleteOperations(playerId, questId)) return;
        QuestProgress before = store.load(playerId, questId).orElse(null);
        if (before == null || before.state() != QuestState.COMPLETING) return;
        QuestProgress after = engine.rewardsComplete(before, clock.now());
        store.commit(before, after,
                UUID.nameUUIDFromBytes(("quest-complete:" + before.occurrenceId()).getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)), List.of());
    }

    private java.time.Instant retryAt(int attempts) {
        long seconds = Math.min(300, 1L << Math.min(8, attempts));
        return clock.now().plus(Duration.ofSeconds(seconds));
    }

    private QuestProgress require(UUID playerId, ContentId questId) {
        return store.load(playerId, questId).orElseThrow(
                () -> new IllegalArgumentException("quest progress does not exist"));
    }

    private QuestDefinition definition(ContentId id) {
        return catalog.get().find(id).orElseThrow(
                () -> new IllegalArgumentException("unknown quest " + id));
    }

    private List<PendingQuestOperation> operationBuilder(
            QuestProgress progress,
            List<com.branz.mmorpg.quest.api.ActionDefinition> actions) {
        ArrayList<PendingQuestOperation> result = new ArrayList<>();
        for (var action : actions) {
            HashMap<String, String> payload = new HashMap<>(action.values());
            action.numbers().forEach((key, value) -> payload.put(key, value.toString()));
            payload.put("required", Boolean.toString(action.required()));
            result.add(new PendingQuestOperation(
                    "quest:" + progress.occurrenceId() + ':' + progress.stageId()
                            + ':' + action.id(),
                    progress.playerId(), progress.questId(), action.type(), payload,
                    PendingQuestOperation.State.PENDING, 0, clock.now(), ""));
        }
        return List.copyOf(result);
    }

    private Materialized materialize(
            QuestProgress progress, List<PendingQuestOperation> operations) {
        HashMap<String, String> flags = new HashMap<>(progress.flags());
        ArrayList<PendingQuestOperation> pending = new ArrayList<>();
        int order = 0;
        for (PendingQuestOperation operation : operations) {
            if (operation.operationType()
                    == com.branz.mmorpg.quest.api.ActionDefinition.Type.SET_FLAG) {
                flags.put(operation.payload().get("flag"),
                        operation.payload().getOrDefault("value", "true"));
            } else if (operation.operationType()
                    == com.branz.mmorpg.quest.api.ActionDefinition.Type.REMOVE_FLAG) {
                flags.remove(operation.payload().get("flag"));
            } else {
                HashMap<String, String> payload = new HashMap<>(operation.payload());
                payload.put("_order", Integer.toString(order++));
                pending.add(new PendingQuestOperation(
                        operation.operationId(), operation.playerId(),
                        operation.questId(), operation.operationType(), payload,
                        operation.state(), operation.attempts(), operation.nextAttemptAt(),
                        operation.lastError()));
            }
        }
        QuestProgress updated = flags.equals(progress.flags()) ? progress
                : new QuestProgress(progress.playerId(), progress.questId(),
                progress.definitionVersion(), progress.revision(), progress.state(),
                progress.stageId(), progress.occurrenceId(), progress.objectives(),
                flags, progress.startedAt(), progress.updatedAt(), progress.completedAt());
        return new Materialized(updated, List.copyOf(pending));
    }

    private record Materialized(
            QuestProgress progress, List<PendingQuestOperation> operations) {
    }

    @FunctionalInterface
    public interface MigrationLookup {
        Optional<QuestMigrationDefinition> find(
                ContentId questId, int fromVersion, int toVersion);
    }
}
