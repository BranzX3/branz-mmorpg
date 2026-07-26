package com.branz.mmorpg.quest.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.quest.api.ActionDefinition;
import com.branz.mmorpg.quest.api.CutsceneAction;
import com.branz.mmorpg.quest.api.CutsceneDefinition;
import com.branz.mmorpg.quest.api.CutsceneSession;
import com.branz.mmorpg.quest.api.DialogueChoice;
import com.branz.mmorpg.quest.api.DialogueDefinition;
import com.branz.mmorpg.quest.api.DialogueNode;
import com.branz.mmorpg.quest.api.ObjectiveDefinition;
import com.branz.mmorpg.quest.api.PendingQuestOperation;
import com.branz.mmorpg.quest.api.QuestDefinition;
import com.branz.mmorpg.quest.api.QuestEvent;
import com.branz.mmorpg.quest.api.QuestGamePort;
import com.branz.mmorpg.quest.api.QuestStageDefinition;
import com.branz.mmorpg.quest.api.QuestState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class QuestGameplayEngineTest {
    private static final ContentId QUEST = ContentId.parse("test:quest");
    private static final ContentId NPC = ContentId.parse("test:npc");
    private static final ContentId ITEM = ContentId.parse("test:item");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void enteringStageImmediatelyReconcilesCanonicalPossession() {
        ObjectiveDefinition talked = objective("talk", ObjectiveDefinition.Type.TALK,
                NPC, 1, ObjectiveDefinition.CreditPolicy.PERSONAL);
        ObjectiveDefinition possess = objective("possess", ObjectiveDefinition.Type.POSSESS,
                ITEM, 3, ObjectiveDefinition.CreditPolicy.PERSONAL);
        QuestStageDefinition first = stage("first", List.of(talked), Optional.of("second"));
        QuestStageDefinition second = stage("second", List.of(possess), Optional.empty());
        QuestDefinition definition = definition(Map.of("first", first, "second", second));
        UUID player = UUID.randomUUID();
        QuestEngine engine = new QuestEngine();
        var started = engine.start(player, definition, Optional.empty(), NOW);

        QuestTransition result = engine.event(started, definition,
                event(player, QuestEvent.Type.NPC_TALKED, NPC, Set.of()),
                game(3), NOW.plusSeconds(1));

        assertTrue(result.changed());
        assertEquals(QuestState.READY_TO_TURN_IN, result.progress().state());
        assertEquals(3, result.progress().objectives().get("possess").current());
    }

    @Test
    void partySnapshotCreditsOnlyCapturedRecipients() {
        UUID killer = UUID.randomUUID();
        UUID nearby = UUID.randomUUID();
        UUID absent = UUID.randomUUID();
        ObjectiveDefinition kill = objective("kill", ObjectiveDefinition.Type.KILL,
                NPC, 1, ObjectiveDefinition.CreditPolicy.PARTY_IN_RANGE);
        ObjectiveEngine engine = new ObjectiveEngine();
        QuestEvent event = event(killer, QuestEvent.Type.MOB_KILLED, NPC, Set.of(nearby));

        assertTrue(engine.reduce(nearby, kill, engine.initial(kill), event, game(0)).complete());
        assertFalse(engine.reduce(absent, kill, engine.initial(kill), event, game(0)).complete());
    }

    @Test
    void dialogueRejectsDoubleClickBySequence() {
        DialogueNode choice = new DialogueNode("choice", DialogueNode.Type.CHOICE,
                "", "", "", DialogueNode.AdvanceMode.CHOICE, 0, Optional.empty(),
                List.of(new DialogueChoice("yes", "yes", List.of(), "",
                        List.of(), "end", true)), List.of(), List.of(), Optional.empty());
        DialogueNode end = new DialogueNode("end", DialogueNode.Type.END,
                "", "", "", DialogueNode.AdvanceMode.MANUAL, 0, Optional.empty(),
                List.of(), List.of(), List.of(), Optional.empty());
        DialogueDefinition definition = new DialogueDefinition(
                ContentId.parse("test:dialogue"), 1, "choice",
                Map.of("choice", choice, "end", end),
                DialogueDefinition.InterruptionPolicy.BLOCKING,
                DialogueDefinition.HistoryPolicy.LINES_AND_CHOICES, Map.of());
        DialogueEngine engine = new DialogueEngine();
        var session = engine.start(definition, UUID.randomUUID(), 1, NOW,
                (player, conditions) -> true, (player, id, sequence, actions) -> {});
        var accepted = engine.advance(definition, session, session.sequence(),
                Optional.of("yes"), (player, conditions) -> true,
                (player, id, sequence, actions) -> {}, NOW);
        var stale = engine.advance(definition, accepted.session(), session.sequence(),
                Optional.of("yes"), (player, conditions) -> true,
                (player, id, sequence, actions) -> {}, NOW);

        assertEquals(DialogueEngine.AdvanceResult.Status.COMPLETE, accepted.status());
        assertEquals(DialogueEngine.AdvanceResult.Status.STALE_INPUT, stale.status());
    }

    @Test
    void cutsceneOrderingSkipAndCleanupAreCanonicalAndIdempotent() {
        CutsceneAction freeze = action("freeze", 0, 0,
                CutsceneAction.Type.FREEZE_INPUT, Map.of("enabled", "true"));
        CutsceneAction later = action("later", 100, 2,
                CutsceneAction.Type.SOUND, Map.of());
        CutsceneAction earlierPriority = action("priority", 100, 1,
                CutsceneAction.Type.DISPLAY, Map.of());
        CutsceneAction finalState = action("final", 0, 0,
                CutsceneAction.Type.PLAYER_TELEPORT, Map.of());
        CutsceneAction skipState = action("skip", 0, 0,
                CutsceneAction.Type.PLAYER_TELEPORT, Map.of());
        CutsceneAction unfreeze = action("unfreeze", 0, 0,
                CutsceneAction.Type.FREEZE_INPUT, Map.of("enabled", "false"));
        CutsceneAction camera = action("camera", 0, 1,
                CutsceneAction.Type.CAMERA_RESTORE, Map.of());
        CutsceneDefinition definition = new CutsceneDefinition(
                ContentId.parse("test:scene"), 1, CutsceneDefinition.Scope.PRIVATE,
                CutsceneDefinition.SkipPolicy.ALWAYS, List.of(freeze),
                List.of(later, earlierPriority), List.of(100L), List.of(finalState),
                List.of(skipState), List.of(unfreeze, camera),
                CutsceneDefinition.DisconnectRecovery.APPLY_SKIP_STATE);
        CutsceneEngine engine = new CutsceneEngine();
        CutsceneSession prepared =
                engine.prepare(definition, Set.of(UUID.randomUUID()), 1_000, NOW).session();
        var advanced = engine.advance(definition, prepared,
                100_001_000, NOW.plusMillis(100));

        assertEquals(List.of("priority", "later", "final"),
                advanced.actions().stream().map(CutsceneAction::id).toList());
        var cleanup = engine.beginCleanup(definition, advanced.session(),
                100_002_000, NOW.plusMillis(101));
        assertFalse(cleanup.session().inputFrozen());
        assertFalse(cleanup.session().cameraAttached());
        assertTrue(engine.beginCleanup(definition, cleanup.session(),
                100_003_000, NOW.plusMillis(102)).actions().isEmpty());
        assertEquals(CutsceneSession.State.COMPLETE,
                engine.cleanupComplete(cleanup.session(), NOW.plusMillis(103)).state());
    }

    private static QuestDefinition definition(Map<String, QuestStageDefinition> stages) {
        return new QuestDefinition(QUEST, 1, "title", "description", "story",
                QuestDefinition.RepeatPolicy.NEVER, List.of(), "manual", "first",
                stages, List.of(), QuestDefinition.MigrationPolicy.SAFE, Set.of(), 1);
    }

    private static QuestStageDefinition stage(
            String id, List<ObjectiveDefinition> objectives, Optional<String> next) {
        return new QuestStageDefinition(id, List.of(), objectives,
                QuestStageDefinition.CompletionPolicy.ALL, 0,
                List.of(), next, Optional.empty(), true);
    }

    private static ObjectiveDefinition objective(
            String id, ObjectiveDefinition.Type type, ContentId target, long amount,
            ObjectiveDefinition.CreditPolicy credit) {
        return new ObjectiveDefinition(id, type, Optional.of(target), amount,
                credit, Set.of(), Map.of());
    }

    private static QuestEvent event(UUID player, QuestEvent.Type type,
                                    ContentId target, Set<UUID> party) {
        return new QuestEvent(UUID.randomUUID(), type, player, Optional.of(target),
                1, "test", party, false, Map.of(), NOW);
    }

    private static QuestGamePort game(long itemQuantity) {
        return new QuestGamePort() {
            @Override public long itemQuantity(UUID playerId, ContentId itemId) {
                return itemQuantity;
            }
            @Override public int masteryLevel(UUID playerId, ContentId masteryId) { return 0; }
            @Override public int partySize(UUID playerId) { return 1; }
            @Override public boolean hasPermission(UUID playerId, String permission) {
                return true;
            }
            @Override public boolean contentUnlocked(UUID playerId, ContentId id) {
                return true;
            }
            @Override public ActionResult execute(PendingQuestOperation operation) {
                return new ActionResult(ActionResult.Status.APPLIED, "");
            }
        };
    }

    private static CutsceneAction action(
            String id, long at, int priority, CutsceneAction.Type type,
            Map<String, String> values) {
        return new CutsceneAction(id, at, priority, CutsceneAction.Track.PLAYER,
                type, values, Map.of());
    }
}
