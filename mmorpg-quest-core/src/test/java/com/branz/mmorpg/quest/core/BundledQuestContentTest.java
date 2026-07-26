package com.branz.mmorpg.quest.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.content.AtomicContentService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BundledQuestContentTest {
    @Test
    void bundledLaunchPathCompilesAgainstBundledGameContent() {
        Path paperResources = Path.of("..", "mmorpg-paper", "src", "main", "resources");
        AtomicContentService game = new AtomicContentService();
        assertTrue(game.reload(paperResources.resolve("content")).successful());
        AtomicQuestContentService quests =
                new AtomicQuestContentService(game::snapshot);

        var result = quests.reload(paperResources.resolve("quest-content"),
                Set.of("dialogue", "cutscene", "actors", "locations", "encounter"));

        assertTrue(result.successful(), () -> result.diagnostics().toString());
        assertTrue(result.catalog().quests().containsKey(
                com.branz.mmorpg.api.content.ContentId.parse("branz:the_old_seal")));
    }

    @Test
    void invalidReloadIsAtomicAndUnknownFieldsAreRejected(@TempDir Path content)
            throws Exception {
        AtomicContentService game = new AtomicContentService();
        AtomicQuestContentService quests =
                new AtomicQuestContentService(game::snapshot);
        Path quest = content.resolve("quest.yml");
        Files.writeString(quest, validQuest(""));
        var first = quests.reload(content, Set.of());
        assertTrue(first.successful(), () -> first.diagnostics().toString());
        long activeRevision = first.catalog().revision();

        Files.writeString(quest, validQuest("typo_field: true\n"));
        var rejected = quests.reload(content, Set.of());

        assertFalse(rejected.successful());
        assertEquals(activeRevision, quests.catalog().revision());
        assertTrue(rejected.diagnostics().stream().anyMatch(value ->
                value.resolution().contains("unknown field")));
    }

    @Test
    void unconditionalStageCycleIsRejected(@TempDir Path content) throws Exception {
        AtomicContentService game = new AtomicContentService();
        AtomicQuestContentService quests =
                new AtomicQuestContentService(game::snapshot);
        Files.writeString(content.resolve("cycle.yml"), """
                type: quest
                id: test:cycle
                version: 1
                title: title
                description: description
                category: story
                repeat_policy: never
                requirements: []
                start_trigger: manual
                start_stage: a
                stages:
                  a:
                    objectives:
                      - {id: wait_a, objective_type: wait, amount: 1}
                    next: b
                  b:
                    objectives:
                      - {id: wait_b, objective_type: wait, amount: 1}
                    next: a
                rewards: []
                migration_policy: safe
                tags: []
                tracking_priority: 1
                """);

        var result = quests.reload(content, Set.of());

        assertFalse(result.successful());
        assertTrue(result.diagnostics().stream().anyMatch(value ->
                value.code().equals("Q-UNBOUNDED-STAGE-CYCLE")));
    }

    private static String validQuest(String extra) {
        return """
                type: quest
                id: test:valid
                version: 1
                title: title
                description: description
                category: story
                repeat_policy: never
                requirements: []
                start_trigger: manual
                start_stage: only
                stages:
                  only:
                    objectives:
                      - {id: wait, objective_type: wait, amount: 1}
                    completion_actions:
                      - {id: finish, action_type: complete_quest}
                rewards: []
                migration_policy: safe
                tags: []
                tracking_priority: 1
                """ + extra;
    }
}
