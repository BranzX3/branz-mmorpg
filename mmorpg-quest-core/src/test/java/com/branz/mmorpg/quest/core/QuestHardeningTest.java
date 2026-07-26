package com.branz.mmorpg.quest.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.quest.api.ObjectiveDefinition;
import com.branz.mmorpg.quest.api.ObjectiveProgress;
import com.branz.mmorpg.quest.api.QuestCatalog;
import com.branz.mmorpg.quest.api.QuestDefinition;
import com.branz.mmorpg.quest.api.QuestEvent;
import com.branz.mmorpg.quest.api.QuestMigrationDefinition;
import com.branz.mmorpg.quest.api.QuestProgress;
import com.branz.mmorpg.quest.api.QuestStageDefinition;
import com.branz.mmorpg.quest.api.QuestState;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class QuestHardeningTest {
    @Test
    void mappedMigrationPreservesBoundedObjectiveProgressAndState() {
        UUID player = UUID.randomUUID();
        ContentId questId = ContentId.parse("test:migrated");
        QuestProgress before = new QuestProgress(player, questId, 1, 7,
                QuestState.MIGRATION_REQUIRED, "old_stage", UUID.randomUUID(),
                Map.of("old_kills", new ObjectiveProgress(8, 10, Map.of())),
                Map.of("_migration_previous_state", "ACTIVE"),
                Instant.EPOCH, Instant.EPOCH, Optional.empty());
        ObjectiveDefinition objective = new ObjectiveDefinition(
                "new_kills", ObjectiveDefinition.Type.KILL,
                Optional.of(ContentId.parse("test:mob")), 5,
                ObjectiveDefinition.CreditPolicy.PERSONAL, Set.of(), Map.of());
        QuestStageDefinition stage = new QuestStageDefinition(
                "new_stage", List.of(), List.of(objective),
                QuestStageDefinition.CompletionPolicy.ALL, 0, List.of(),
                Optional.empty(), Optional.empty(), true);
        QuestDefinition target = new QuestDefinition(questId, 2, "title",
                "description", "story", QuestDefinition.RepeatPolicy.NEVER,
                List.of(), "manual", "new_stage", Map.of("new_stage", stage),
                List.of(), QuestDefinition.MigrationPolicy.REQUIRES_MAPPING,
                Set.of(), 1);

        QuestProgress after = new QuestMigrationEngine().migrate(before, target,
                new QuestMigrationDefinition(questId, 1, 2,
                        Map.of("old_stage", "new_stage"),
                        Map.of("old_kills", "new_kills")), Instant.ofEpochSecond(1));

        assertEquals(2, after.definitionVersion());
        assertEquals(QuestState.ACTIVE, after.state());
        assertEquals(5, after.objectives().get("new_kills").current());
        assertTrue(!after.flags().containsKey("_migration_previous_state"));
    }

    @Test
    void objectiveIndexBuildsLargeCatalogWithinBoundAndRoutesCandidates() {
        QuestCatalog catalog = assertTimeout(Duration.ofSeconds(2), () -> {
            HashMap<ContentId, QuestDefinition> quests = new HashMap<>();
            for (int index = 0; index < 10_000; index++) {
                ContentId id = ContentId.parse("load:q_" + index);
                ObjectiveDefinition objective = new ObjectiveDefinition("objective",
                        index % 2 == 0 ? ObjectiveDefinition.Type.KILL
                                : ObjectiveDefinition.Type.CRAFT,
                        Optional.of(ContentId.parse(index % 2 == 0
                                ? "load:mob" : "load:recipe")), 1,
                        ObjectiveDefinition.CreditPolicy.PERSONAL, Set.of(), Map.of());
                QuestStageDefinition stage = new QuestStageDefinition(
                        "stage", List.of(), List.of(objective),
                        QuestStageDefinition.CompletionPolicy.ALL, 0, List.of(),
                        Optional.empty(), Optional.empty(), true);
                quests.put(id, new QuestDefinition(id, 1, "title", "description",
                        "load", QuestDefinition.RepeatPolicy.NEVER, List.of(),
                        "manual", "stage", Map.of("stage", stage), List.of(),
                        QuestDefinition.MigrationPolicy.SAFE, Set.of(), 1));
            }
            return new QuestCatalog(9, Instant.now(), quests, Map.of(), Map.of());
        });

        ObjectiveIndex index = assertTimeout(
                Duration.ofSeconds(2), () -> ObjectiveIndex.build(catalog));

        assertEquals(5_000, index.candidates(QuestEvent.Type.MOB_KILLED).size());
        assertEquals(5_000, index.candidates(QuestEvent.Type.CRAFT_COMPLETED).size());
        assertTrue(index.candidates(QuestEvent.Type.NPC_TALKED).isEmpty());
    }

    @Test
    void partyVoteUsesFrozenEligibilityAndDeterministicTieChoice() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        PartyVoteEngine engine = new PartyVoteEngine();
        Instant expiry = Instant.parse("2026-01-01T00:01:00Z");
        var vote = engine.begin(PartyVoteEngine.Policy.MAJORITY_VOTE,
                leader, Set.of(leader, member), Set.of("left", "right"), expiry);
        vote = engine.vote(vote, leader, "left", expiry.minusSeconds(10));
        vote = engine.vote(vote, member, "right", expiry.minusSeconds(9));

        assertEquals("right", engine.result(
                vote, expiry.plusSeconds(1), "right").orElseThrow());
    }
}
