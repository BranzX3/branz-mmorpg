package com.branz.mmorpg.quest.core;

import com.branz.mmorpg.quest.api.ObjectiveProgress;
import com.branz.mmorpg.quest.api.QuestDefinition;
import com.branz.mmorpg.quest.api.QuestMigrationDefinition;
import com.branz.mmorpg.quest.api.QuestProgress;
import com.branz.mmorpg.quest.api.QuestState;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Pure state transformer; never silently discards an active objective. */
public final class QuestMigrationEngine {
    public QuestProgress migrate(
            QuestProgress before, QuestDefinition target,
            QuestMigrationDefinition mapping, Instant now) {
        if (!before.questId().equals(target.id())
                || !mapping.questId().equals(target.id())
                || before.definitionVersion() != mapping.fromVersion()
                || target.version() != mapping.toVersion()) {
            throw new IllegalArgumentException("migration does not match progress/target");
        }
        String stageId = mapping.stageMappings()
                .getOrDefault(before.stageId(), before.stageId());
        var targetStage = target.stages().get(stageId);
        if (targetStage == null) {
            throw new IllegalStateException("migration has no valid stage mapping for "
                    + before.stageId());
        }
        HashMap<String, ObjectiveProgress> objectives = new HashMap<>();
        targetStage.objectives().forEach(definition -> {
            ObjectiveProgress prior = before.objectives().entrySet().stream()
                    .filter(entry -> mapping.objectiveMappings()
                            .getOrDefault(entry.getKey(), entry.getKey())
                            .equals(definition.id()))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
            objectives.put(definition.id(), prior == null
                    ? new ObjectiveProgress(0, definition.targetAmount(), Map.of())
                    : new ObjectiveProgress(Math.min(prior.current(),
                    definition.targetAmount()), definition.targetAmount(), prior.data()));
        });
        HashMap<String, String> flags = new HashMap<>(before.flags());
        QuestState state = before.state();
        if (state == QuestState.MIGRATION_REQUIRED) {
            String previous = flags.remove("_migration_previous_state");
            state = previous == null ? QuestState.ACTIVE : QuestState.valueOf(previous);
        }
        return new QuestProgress(before.playerId(), before.questId(), target.version(),
                before.revision() + 1, state, stageId, before.occurrenceId(),
                objectives, flags, before.startedAt(), now, before.completedAt());
    }

    public QuestProgress markRequired(QuestProgress before, Instant now) {
        if (before.state() == QuestState.MIGRATION_REQUIRED) return before;
        HashMap<String, String> flags = new HashMap<>(before.flags());
        flags.put("_migration_previous_state", before.state().name());
        return new QuestProgress(before.playerId(), before.questId(),
                before.definitionVersion(), before.revision() + 1,
                QuestState.MIGRATION_REQUIRED, before.stageId(), before.occurrenceId(),
                before.objectives(), flags, before.startedAt(), now, Optional.empty());
    }
}
