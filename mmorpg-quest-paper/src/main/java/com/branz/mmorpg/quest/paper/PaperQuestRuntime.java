package com.branz.mmorpg.quest.paper;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.economy.AdminCurrencyPort;
import com.branz.mmorpg.api.item.InventoryService;
import com.branz.mmorpg.api.mastery.CombatMasteryService;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.api.runtime.Scheduler;
import com.branz.mmorpg.api.social.PartyService;
import com.branz.mmorpg.api.telemetry.TelemetryService;
import com.branz.mmorpg.quest.api.ActionDefinition;
import com.branz.mmorpg.quest.api.AccessibilitySettings;
import com.branz.mmorpg.quest.api.CutsceneAction;
import com.branz.mmorpg.quest.api.CutsceneDefinition;
import com.branz.mmorpg.quest.api.CutsceneSession;
import com.branz.mmorpg.quest.api.DialogueDefinition;
import com.branz.mmorpg.quest.api.DialogueHistoryEntry;
import com.branz.mmorpg.quest.api.DialogueNode;
import com.branz.mmorpg.quest.api.DialogueSession;
import com.branz.mmorpg.quest.api.PendingQuestOperation;
import com.branz.mmorpg.quest.api.QuestActorPort;
import com.branz.mmorpg.quest.api.QuestAudience;
import com.branz.mmorpg.quest.api.QuestCameraPort;
import com.branz.mmorpg.quest.api.QuestCatalogReload;
import com.branz.mmorpg.quest.api.QuestEvent;
import com.branz.mmorpg.quest.api.QuestGamePort;
import com.branz.mmorpg.quest.api.QuestLocationPort;
import com.branz.mmorpg.quest.api.QuestProgress;
import com.branz.mmorpg.quest.api.QuestService;
import com.branz.mmorpg.quest.core.AtomicQuestContentService;
import com.branz.mmorpg.quest.core.CutsceneEngine;
import com.branz.mmorpg.quest.core.ConditionEngine;
import com.branz.mmorpg.quest.core.DefaultQuestService;
import com.branz.mmorpg.quest.core.DialogueEngine;
import com.branz.mmorpg.quest.storage.JdbcQuestProgressStore;
import com.branz.mmorpg.quest.storage.JdbcQuestSessionStore;
import com.branz.mmorpg.quest.storage.JdbcQuestWorldStore;
import com.branz.mmorpg.quest.storage.JdbcQuestUnlockStore;
import com.branz.mmorpg.api.crafting.CraftJob;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.storage.DatabaseManager;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Root-facing Quest adapter. All SQL is scheduled off-thread; Paper mutations
 * return through the owning thread.
 */
public final class PaperQuestRuntime implements Listener, QuestAudience,
        QuestActorPort, QuestCameraPort, QuestLocationPort {
    private final JavaPlugin plugin;
    private final Scheduler scheduler;
    private final Path contentDirectory;
    private final AtomicQuestContentService content;
    private final JdbcQuestProgressStore progressStore;
    private final JdbcQuestSessionStore sessionStore;
    private final JdbcQuestWorldStore worldStore;
    private final PartyService parties;
    private final TelemetryService telemetry;
    private final PaperQuestGamePort game;
    private final DefaultQuestService quests;
    private final DialogueEngine dialogues = new DialogueEngine();
    private final ConditionEngine conditionEngine = new ConditionEngine();
    private final CutsceneEngine cutscenes = new CutsceneEngine();
    private final GameClock clock;
    private final Map<UUID, DialogueSession> dialogueSessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerDialogue = new ConcurrentHashMap<>();
    private final Map<UUID, CutsceneSession> cutsceneSessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> actors = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> sessionActors = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> privateActorViewers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, org.bukkit.block.data.BlockData>>
            privateBlockChanges = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> cameraModes = new ConcurrentHashMap<>();
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Map<String, Location> locations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sneakDebounce = new ConcurrentHashMap<>();
    private final Map<String, ContentId> worldObjects = new ConcurrentHashMap<>();
    private final Map<UUID, ContentId> playerRegions = new ConcurrentHashMap<>();
    private final Map<UUID, AccessibilitySettings> accessibility =
            new ConcurrentHashMap<>();
    private volatile Map<String, String> localization = Map.of();
    private final java.util.concurrent.atomic.AtomicLong nextPendingRetryMillis =
            new java.util.concurrent.atomic.AtomicLong();
    private final NamespacedKey npcKey;
    private final NamespacedKey dialogueKey;
    private final NamespacedKey mobDefinitionKey;
    private final NamespacedKey actorSessionKey;
    private volatile EncounterStarter encounterStarter =
            (instance, definition, participants, arena) ->
                    java.util.concurrent.CompletableFuture.failedFuture(
                            new IllegalStateException("encounter runtime is unavailable"));

    @FunctionalInterface
    public interface EncounterStarter {
        java.util.concurrent.CompletableFuture<Void> start(
                UUID instanceId, ContentId definitionId,
                Set<UUID> participants, Location arena);
    }

    public void encounterStarter(EncounterStarter starter) {
        encounterStarter = java.util.Objects.requireNonNull(starter, "starter");
    }

    public PaperQuestRuntime(
            JavaPlugin plugin, DatabaseManager database, ContentService gameContent,
            Scheduler scheduler, InventoryService inventory,
            CombatMasteryService mastery, PartyService parties,
            AdminCurrencyPort currency, Path contentDirectory, GameClock clock,
            TelemetryService telemetry) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.contentDirectory = java.util.Objects.requireNonNull(contentDirectory, "contentDirectory");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.parties = java.util.Objects.requireNonNull(parties, "parties");
        this.telemetry = java.util.Objects.requireNonNull(telemetry, "telemetry");
        content = new AtomicQuestContentService(gameContent::snapshot);
        progressStore = new JdbcQuestProgressStore(database);
        sessionStore = new JdbcQuestSessionStore(database);
        worldStore = new JdbcQuestWorldStore(database);
        game = new PaperQuestGamePort(plugin, inventory, mastery, parties, currency,
                new JdbcQuestUnlockStore(database));
        quests = new DefaultQuestService(progressStore, game, content::catalog, clock,
                content::migration);
        npcKey = new NamespacedKey(plugin, "quest_npc");
        dialogueKey = new NamespacedKey(plugin, "quest_dialogue");
        mobDefinitionKey = new NamespacedKey(plugin, "mob_definition");
        actorSessionKey = new NamespacedKey(plugin, "quest_actor_session");
        cleanupOrphanActors();
        game.presentation(this::presentationAction);
        QuestCatalogReload load = content.reload(contentDirectory,
                Set.of("dialogue", "cutscene", "actors", "locations", "encounter"));
        if (!load.successful()) {
            throw new IllegalStateException("quest content invalid: "
                    + load.diagnostics());
        }
        loadLocalization();
        validateLocalization();
        recover();
    }

    public QuestService quests() { return quests; }
    public com.branz.mmorpg.quest.api.QuestContentService content() { return content; }
    public JdbcQuestProgressStore progressStore() { return progressStore; }
    public JdbcQuestSessionStore sessionStore() { return sessionStore; }

    public QuestCatalogReload reload() {
        QuestCatalogReload result = content.reload(contentDirectory,
                Set.of("dialogue", "cutscene", "actors", "locations", "encounter"));
        if (result.successful()) {
            loadLocalization();
            validateLocalization();
        }
        return result;
    }

    public void tick() {
        Instant now = clock.now();
        long wallNow = System.currentTimeMillis();
        long retryAt = nextPendingRetryMillis.get();
        if (wallNow >= retryAt
                && nextPendingRetryMillis.compareAndSet(retryAt, wallNow + 1_000)) {
            scheduler.async(() -> quests.retryPending(100)).exceptionally(failure -> {
                plugin.getLogger().warning("Quest pending operation retry failed: "
                        + root(failure));
                return 0;
            });
        }
        for (DialogueSession session : List.copyOf(dialogueSessions.values())) {
            if (session.state() != DialogueSession.State.ACTIVE) continue;
            DialogueDefinition definition =
                    content.catalog().dialogue(session.dialogueId()).orElse(null);
            if (definition == null) continue;
            DialogueNode node = definition.nodes().get(session.currentNode());
            AccessibilitySettings settings = accessibility.getOrDefault(
                    session.playerId(), AccessibilitySettings.defaults(session.playerId()));
            if (node != null && node.type() == DialogueNode.Type.LINE
                    && settings.dialogueMode() != AccessibilitySettings.DialogueMode.MANUAL) {
                long delay = settings.dialogueMode()
                        == AccessibilitySettings.DialogueMode.FAST ? 250
                        : Math.max(500, Math.round(
                        translate(node.textKey()).length() * 40 / settings.textSpeed()));
                if (!now.isBefore(session.lastActiveAt().plusMillis(delay))) {
                    advanceDialogue(session.playerId(), session.sessionId(),
                            session.sequence(), Optional.empty(), ignored -> {});
                    continue;
                }
            }
            if (node != null && (node.advanceMode()
                    == DialogueNode.AdvanceMode.AUTO_AFTER_DURATION
                    || node.type() == DialogueNode.Type.WAIT)
                    && !now.isBefore(session.lastActiveAt()
                    .plusMillis(node.durationMillis()))) {
                advanceDialogue(session.playerId(), session.sessionId(),
                        session.sequence(), Optional.empty(), ignored -> { });
            }
        }
        long monotonic = clock.monotonicNanos();
        for (CutsceneSession session : List.copyOf(cutsceneSessions.values())) {
            if (session.state() != CutsceneSession.State.PLAYING) continue;
            CutsceneDefinition definition =
                    content.catalog().cutscene(session.cutsceneId()).orElse(null);
            if (definition == null) continue;
            CutsceneEngine.Step step =
                    cutscenes.advance(definition, session, monotonic, now);
            executeCutscene(step.actions(), step.session());
            if (!step.actions().isEmpty()
                    || step.session().state() != session.state()) {
                persistCutscene(step.session());
            }
            cutsceneSessions.put(session.sessionId(), step.session());
            if (step.session().state() == CutsceneSession.State.COMPLETING) {
                cleanupCutscene(definition, step.session());
            }
        }
    }

    /** Restores every Paper-side lock before the plugin scheduler is stopped. */
    public void shutdown() {
        for (CutsceneSession session : List.copyOf(cutsceneSessions.values())) {
            if (session.state() == CutsceneSession.State.COMPLETE) continue;
            CutsceneDefinition definition =
                    content.catalog().cutscene(session.cutsceneId()).orElse(null);
            CutsceneSession saved = session;
            if (definition != null && session.state() == CutsceneSession.State.PLAYING) {
                CutsceneEngine.Step recovery = cutscenes.disconnect(
                        definition, session, clock.monotonicNanos(), clock.now());
                executeCutscene(recovery.actions(), recovery.session());
                saved = recovery.session();
            }
            persistCutscene(saved);
            for (UUID playerId : session.participantSnapshot()) {
                restore(playerId, session.sessionId());
                frozen.remove(playerId);
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) player.setInvulnerable(false);
            }
            Optional.ofNullable(sessionActors.remove(session.sessionId()))
                    .orElse(Set.of()).forEach(this::despawn);
            restorePrivateBlocks(session.sessionId());
        }
    }

    public void startQuest(UUID playerId, ContentId questId, Consumer<String> feedback) {
        scheduler.async(() -> quests.start(playerId, questId))
                .whenComplete((result, failure) -> scheduler.sync(() ->
                        {
                            if (failure == null) telemetry.increment("quest.started");
                            feedback.accept(failure == null
                                    ? "Started " + result.questId()
                                    : "Quest start failed: " + root(failure));
                        }));
    }

    public void turnIn(UUID playerId, ContentId questId, Consumer<String> feedback) {
        scheduler.async(() -> quests.turnIn(playerId, questId))
                .whenComplete((result, failure) -> scheduler.sync(() ->
                        feedback.accept(failure == null
                                ? "Quest rewards queued safely."
                                : "Turn-in failed: " + root(failure))));
    }

    public void abandon(UUID playerId, ContentId questId, Consumer<String> feedback) {
        scheduler.async(() -> quests.abandon(playerId, questId))
                .whenComplete((result, failure) -> scheduler.sync(() ->
                        feedback.accept(failure == null
                                ? "Quest abandoned." : "Abandon failed: " + root(failure))));
    }

    public void inspect(UUID playerId, Optional<ContentId> questId, Consumer<String> feedback) {
        scheduler.async(() -> questId.isPresent()
                        ? quests.progress(playerId, questId.orElseThrow()).stream().toList()
                        : List.copyOf(quests.active(playerId)))
                .whenComplete((values, failure) -> scheduler.sync(() ->
                        feedback.accept(failure == null ? values.toString()
                                : "Quest inspect failed: " + root(failure))));
    }

    public void history(UUID playerId, ContentId dialogueId, Consumer<String> feedback) {
        scheduler.async(() -> sessionStore.read(playerId, dialogueId, 50))
                .whenComplete((values, failure) -> scheduler.sync(() ->
                        feedback.accept(failure == null ? values.toString()
                                : "History failed: " + root(failure))));
    }

    public void journal(UUID playerId) {
        scheduler.async(() -> quests.active(playerId)).whenComplete((values, failure) ->
                scheduler.sync(() -> {
                    if (failure != null) return;
                    journal(playerId, values.stream().map(value ->
                            new QuestAudience.JournalEntry(value.questId().toString(),
                                    content.catalog().find(value.questId())
                                            .map(def -> def.titleKey()).orElse(value.questId().value()),
                                    value.state().name(), value.stageId())).toList());
                }));
    }

    public void retryPending(Consumer<String> feedback) {
        scheduler.async(() -> quests.retryPending(100))
                .whenComplete((count, failure) -> scheduler.sync(() ->
                        feedback.accept(failure == null
                                ? "Retried " + count + " quest operation(s)."
                                : "Retry failed: " + root(failure))));
    }

    public void bossDefeated(
            UUID playerId, ContentId encounterId, Set<UUID> eligibleSnapshot) {
        publish(new QuestEvent(UUID.randomUUID(), QuestEvent.Type.BOSS_DEFEATED,
                playerId, Optional.of(encounterId), 1, "encounter",
                eligibleSnapshot, true, Map.of(), clock.now()));
    }

    public void itemAcquired(
            UUID playerId, ContentId itemId, long quantity, OperationId operationId) {
        publish(new QuestEvent(UUID.nameUUIDFromBytes(
                ("quest-inventory:" + operationId).getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)),
                QuestEvent.Type.ITEM_ACQUIRED, playerId, Optional.of(itemId),
                quantity, "inventory", Set.of(), false, Map.of(), clock.now()));
    }

    public void masteryChanged(
            UUID playerId, ContentId masteryId, OperationId operationId) {
        publish(new QuestEvent(UUID.nameUUIDFromBytes(
                ("quest-mastery:" + operationId).getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)),
                QuestEvent.Type.MASTERY_CHANGED, playerId, Optional.of(masteryId),
                1, "mastery", Set.of(), false, Map.of(), clock.now()));
    }

    public void skillUsed(UUID playerId, ContentId skillId) {
        publish(new QuestEvent(UUID.randomUUID(), QuestEvent.Type.SKILL_USED,
                playerId, Optional.of(skillId), 1, "skill", Set.of(),
                false, Map.of(), clock.now()));
    }

    public void craftCompleted(CraftJob job) {
        publish(new QuestEvent(UUID.nameUUIDFromBytes(
                ("quest-craft:" + job.operationId()).getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)),
                QuestEvent.Type.CRAFT_COMPLETED, job.playerId(),
                Optional.of(job.recipeId()), 1, "craft", Set.of(),
                false, Map.of(), clock.now()));
    }

    public void reset(UUID playerId, ContentId questId, UUID actor,
                      String reason, Consumer<String> feedback) {
        scheduler.async(() -> progressStore.reset(playerId, questId, actor, reason))
                .whenComplete((removed, failure) -> scheduler.sync(() ->
                        feedback.accept(failure == null
                                ? (removed ? "Quest reset." : "No progress existed.")
                                : "Reset failed: " + root(failure))));
    }

    public void migrate(UUID playerId, ContentId questId, UUID actor,
                        String reason, Consumer<String> feedback) {
        scheduler.async(() -> quests.migrate(playerId, questId, actor, reason))
                .whenComplete((result, failure) -> scheduler.sync(() ->
                        feedback.accept(failure == null
                                ? "Quest migrated to definition version "
                                + result.definitionVersion() + "."
                                : "Migration failed: " + root(failure))));
    }

    public void setStage(UUID playerId, ContentId questId, String stageId,
                         UUID actor, String reason, Consumer<String> feedback) {
        scheduler.async(() -> quests.setStage(
                        playerId, questId, stageId, actor, reason))
                .whenComplete((result, failure) -> scheduler.sync(() ->
                        feedback.accept(failure == null
                                ? "Quest moved to stage " + result.stageId() + "."
                                : "Stage repair failed: " + root(failure))));
    }

    public void setObjective(
            UUID playerId, ContentId questId, String objectiveId, long value,
            UUID actor, String reason, Consumer<String> feedback) {
        scheduler.async(() -> quests.setObjective(
                        playerId, questId, objectiveId, value, actor, reason))
                .whenComplete((result, failure) -> scheduler.sync(() ->
                        feedback.accept(failure == null
                                ? "Quest objective repaired at revision "
                                + result.revision() + "."
                                : "Objective repair failed: " + root(failure))));
    }

    public void advanceDialogue(UUID playerId, UUID sessionId, long sequence,
                                Optional<String> choice, Consumer<String> feedback) {
        DialogueSession captured = dialogueSessions.get(sessionId);
        if (captured == null || !captured.playerId().equals(playerId)) {
            feedback.accept("Dialogue session is not active.");
            return;
        }
        scheduler.async(() -> {
            synchronized (dialogueSessions) {
                DialogueSession before = dialogueSessions.get(sessionId);
                if (before == null) throw new IllegalStateException(
                        "dialogue session is no longer active");
                DialogueDefinition definition =
                        content.catalog().dialogue(before.dialogueId()).orElseThrow();
                Map<ContentId, QuestProgress> progress =
                        quests.active(playerId).stream().collect(
                                java.util.stream.Collectors.toMap(
                                        QuestProgress::questId, value -> value));
                DialogueEngine.AdvanceResult result = dialogues.advance(
                        definition, before, sequence, choice,
                        (player, conditions) -> conditions.stream().allMatch(condition ->
                                conditionEngine.evaluate(condition, player, progress,
                                        game, clock.now()) == ConditionEngine.Result.TRUE),
                        this::executeDialogueActions, clock.now());
                dialogueSessions.put(sessionId, result.session());
                if (result.session().state() == DialogueSession.State.COMPLETE
                        || result.session().state() == DialogueSession.State.CANCELLED) {
                    playerDialogue.remove(playerId, sessionId);
                }
                return result;
            }
        }).whenComplete((result, failure) -> scheduler.sync(() -> {
            if (failure != null) {
                feedback.accept("Dialogue advance failed: " + root(failure));
                return;
            }
            if (result.status() == DialogueEngine.AdvanceResult.Status.STALE_INPUT) {
                telemetry.increment("dialogue.stale_input");
                feedback.accept("Stale dialogue input ignored.");
                return;
            }
            persistDialogue(result.session());
            telemetry.increment("dialogue.advance");
            present(result.session());
            feedback.accept(result.detail());
        }));
    }

    public void skipCutscene(UUID playerId, UUID sessionId, Consumer<String> feedback) {
        CutsceneSession before = cutsceneSessions.get(sessionId);
        if (before == null || !before.participantSnapshot().contains(playerId)) {
            feedback.accept("Cutscene session is not active.");
            return;
        }
        CutsceneDefinition definition =
                content.catalog().cutscene(before.cutsceneId()).orElseThrow();
        CutsceneEngine.Step step = cutscenes.skip(
                definition, before, clock.monotonicNanos(), clock.now());
        executeCutscene(step.actions(), step.session());
        persistCutscene(step.session());
        cutsceneSessions.put(sessionId, step.session());
        cleanupCutscene(definition, step.session());
        telemetry.increment("cutscene.skipped");
        feedback.accept("Cutscene skipped to canonical state.");
    }

    public void playCutscene(UUID playerId, ContentId id, Consumer<String> feedback) {
        try {
            startCutscene(playerId, id);
            feedback.accept("Cutscene started.");
        } catch (RuntimeException failure) {
            feedback.accept("Cutscene failed: " + root(failure));
        }
    }

    public void startDialogueCommand(
            UUID playerId, ContentId id, Consumer<String> feedback) {
        try {
            startDialogue(playerId, id);
            feedback.accept("Dialogue started.");
        } catch (RuntimeException failure) {
            feedback.accept("Dialogue failed: " + root(failure));
        }
    }

    public Collection<DialogueSession> dialogueSessions() {
        return List.copyOf(dialogueSessions.values());
    }

    public Collection<CutsceneSession> cutsceneSessions() {
        return List.copyOf(cutsceneSessions.values());
    }

    public UUID createNpc(Location location, ContentId npcId, ContentId dialogueId) {
        Villager npc = location.getWorld().spawn(location, Villager.class, value -> {
            value.setAI(false);
            value.setInvulnerable(true);
            value.setRemoveWhenFarAway(false);
            value.customName(Component.text(npcId.value()));
            value.setCustomNameVisible(true);
            value.getPersistentDataContainer().set(
                    npcKey, PersistentDataType.STRING, npcId.toString());
            value.getPersistentDataContainer().set(
                    dialogueKey, PersistentDataType.STRING, dialogueId.toString());
        });
        return npc.getUniqueId();
    }

    public String bootstrapReference(Location origin) {
        Location npc = origin.clone().add(4, 0, 0);
        Location gate = origin.clone().add(10, 0, 0);
        Location pedestal = origin.clone().add(14, 0, 0);
        captureLocation("branz:old_ruins_gate", gate);
        captureLocation("branz:seal_pedestal", pedestal);
        pedestal.getBlock().setType(org.bukkit.Material.LODESTONE);
        bindWorldObject(pedestal, ContentId.parse("branz:seal_pedestal"));
        boolean exists = origin.getWorld().getEntitiesByClass(Villager.class).stream()
                .anyMatch(entity -> "branz:seal_keeper".equals(
                        entity.getPersistentDataContainer().get(
                                npcKey, PersistentDataType.STRING)));
        UUID entity = exists ? null : createNpc(npc,
                ContentId.parse("branz:seal_keeper"),
                ContentId.parse("branz:keeper_warning"));
        return "Reference path ready: keeper=" + (entity == null ? "existing" : entity)
                + ", gate=" + blockKey(gate) + ", pedestal=" + blockKey(pedestal);
    }

    public void bindWorldObject(
            Location location, ContentId objectId) {
        worldObjects.put(blockKey(location), objectId);
        JdbcQuestWorldStore.WorldObjectRecord record =
                new JdbcQuestWorldStore.WorldObjectRecord(location.getWorld().getUID(),
                        location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                        objectId);
        scheduler.async(() -> worldStore.save(record));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        game.capturePermissions(event.getPlayer());
        UUID playerId = event.getPlayer().getUniqueId();
        scheduler.async(() -> sessionStore.load(playerId))
                .thenAccept(settings -> accessibility.put(playerId, settings));
        privateActorViewers.forEach((actorId, viewers) -> {
            Entity actor = plugin.getServer().getEntity(actorId);
            if (actor != null && !viewers.contains(playerId)) {
                event.getPlayer().hideEntity(plugin, actor);
            }
        });
        dialogueSessions.values().stream()
                .filter(value -> value.playerId().equals(event.getPlayer().getUniqueId()))
                .findFirst().ifPresent(this::present);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        accessibility.remove(playerId);
        game.forget(playerId);
        UUID dialogueId = playerDialogue.get(playerId);
        if (dialogueId != null) {
            DialogueSession before = dialogueSessions.get(dialogueId);
            if (before != null) {
                DialogueSession paused = dialogues.interrupt(before, true, clock.now());
                saveDialogue(paused);
            }
        }
        cutsceneSessions.values().stream()
                .filter(value -> value.participantSnapshot().contains(playerId))
                .forEach(session -> {
                    CutsceneDefinition definition =
                            content.catalog().cutscene(session.cutsceneId()).orElse(null);
                    if (definition == null) return;
                    CutsceneEngine.Step step = cutscenes.disconnect(
                            definition, session, clock.monotonicNanos(), clock.now());
                    executeCutscene(step.actions(), step.session());
                    persistCutscene(step.session());
                    cutsceneSessions.put(session.sessionId(), step.session());
                    if (step.session().state() == CutsceneSession.State.COMPLETING
                            || step.session().state() == CutsceneSession.State.FAILED) {
                        cleanupCutscene(definition, step.session());
                    }
                });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        String definition = event.getEntity().getPersistentDataContainer()
                .get(mobDefinitionKey, PersistentDataType.STRING);
        if (definition == null) return;
        publishWithParty(QuestEvent.Type.MOB_KILLED, killer,
                ContentId.parse(definition), "combat");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNpc(PlayerInteractAtEntityEvent event) {
        String npc = event.getRightClicked().getPersistentDataContainer()
                .get(npcKey, PersistentDataType.STRING);
        if (npc == null) return;
        event.setCancelled(true);
        String dialogue = event.getRightClicked().getPersistentDataContainer()
                .get(dialogueKey, PersistentDataType.STRING);
        publishWithParty(QuestEvent.Type.NPC_TALKED, event.getPlayer(),
                ContentId.parse(npc), "npc");
        if (dialogue != null) startDialogue(
                event.getPlayer().getUniqueId(), ContentId.parse(dialogue));
    }

    @EventHandler public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        long now = System.currentTimeMillis();
        Long previous = sneakDebounce.put(event.getPlayer().getUniqueId(), now);
        if (previous != null && now - previous < 250) return;
        UUID sessionId = playerDialogue.get(event.getPlayer().getUniqueId());
        DialogueSession session = sessionId == null ? null : dialogueSessions.get(sessionId);
        if (session != null) advanceDialogue(event.getPlayer().getUniqueId(),
                sessionId, session.sequence(), Optional.empty(), ignored -> { });
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrozen(org.bukkit.event.player.PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) return;
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        locations.entrySet().stream()
                .filter(entry -> entry.getKey().contains(":"))
                .filter(entry -> entry.getValue().getWorld().equals(event.getPlayer().getWorld()))
                .filter(entry -> entry.getValue().distanceSquared(
                        event.getPlayer().getLocation()) <= 9)
                .findFirst().ifPresent(entry -> {
                    ContentId region;
                    try {
                        region = ContentId.parse(entry.getKey());
                    } catch (RuntimeException invalid) {
                        return;
                    }
                    if (!region.equals(playerRegions.put(
                            event.getPlayer().getUniqueId(), region))) {
                        game.region(event.getPlayer().getUniqueId(), region);
                        publish(new QuestEvent(UUID.randomUUID(),
                                QuestEvent.Type.REGION_ENTERED,
                                event.getPlayer().getUniqueId(), Optional.of(region), 1,
                                "region", Set.of(), false, Map.of(), clock.now()));
                    }
                });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWorldObject(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) return;
        ContentId object = worldObjects.get(blockKey(
                event.getClickedBlock().getLocation()));
        if (object == null) return;
        event.setCancelled(true);
        publish(new QuestEvent(UUID.randomUUID(),
                QuestEvent.Type.WORLD_OBJECT_INTERACTED,
                event.getPlayer().getUniqueId(), Optional.of(object), 1,
                "world_object", Set.of(), false, Map.of(), clock.now()));
    }

    private void publish(QuestEvent event) {
        scheduler.async(() -> processEvent(event));
    }

    private void publishWithParty(QuestEvent.Type type, Player player,
                                  ContentId target, String source) {
        UUID playerId = player.getUniqueId();
        UUID worldId = player.getWorld().getUID();
        double x = player.getLocation().getX();
        double y = player.getLocation().getY();
        double z = player.getLocation().getZ();
        Map<UUID, MemberPosition> positions = new java.util.HashMap<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            Location location = online.getLocation();
            positions.put(online.getUniqueId(), new MemberPosition(
                    location.getWorld().getUID(), location.getX(),
                    location.getY(), location.getZ()));
        }
        scheduler.async(() -> {
            Set<UUID> nearby = parties.party(playerId).map(party -> {
                double maximum = party.rewardRange() * party.rewardRange();
                return party.members().stream().filter(member -> {
                    MemberPosition position = positions.get(member);
                    if (position == null) return false;
                    if (party.rewardsRequireSameWorld()
                            && !position.worldId().equals(worldId)) return false;
                    double dx = position.x() - x;
                    double dy = position.y() - y;
                    double dz = position.z() - z;
                    return dx * dx + dy * dy + dz * dz <= maximum;
                }).collect(java.util.stream.Collectors.toUnmodifiableSet());
            }).orElse(Set.of());
            return processEvent(new QuestEvent(UUID.randomUUID(), type, playerId,
                    Optional.of(target), 1, source, nearby, false,
                    Map.of(), clock.now()));
        });
    }

    private Collection<QuestProgress> processEvent(QuestEvent event) {
        telemetry.increment("quest.objective_event." + event.type().name()
                .toLowerCase(java.util.Locale.ROOT));
        content.catalog().quests().values().stream()
                .filter(definition -> triggerMatches(definition.startTrigger(), event.type()))
                .filter(definition -> {
                    var stage = definition.stages().get(definition.startStage());
                    return stage.objectives().stream().anyMatch(objective ->
                            objective.targetId().isEmpty()
                                    || objective.targetId().equals(event.targetId()));
                })
                .forEach(definition -> {
                    if (quests.progress(event.playerId(), definition.id()).isPresent()) return;
                    try {
                        quests.start(event.playerId(), definition.id());
                        telemetry.increment("quest.started");
                    } catch (IllegalStateException ignored) {
                        // Requirements/repeat policy remain authoritative.
                    }
                });
        Collection<QuestProgress> result = quests.process(event);
        if (!result.isEmpty()) telemetry.increment("quest.progress_changed");
        quests.retryPending(25);
        return result;
    }

    private static boolean triggerMatches(String trigger, QuestEvent.Type type) {
        return switch (trigger.toLowerCase(java.util.Locale.ROOT)) {
            case "npc", "talk" -> type == QuestEvent.Type.NPC_TALKED;
            case "region" -> type == QuestEvent.Type.REGION_ENTERED;
            case "world_object", "interact" ->
                    type == QuestEvent.Type.WORLD_OBJECT_INTERACTED;
            case "kill" -> type == QuestEvent.Type.MOB_KILLED;
            default -> false;
        };
    }

    private QuestGamePort.ActionResult presentationAction(PendingQuestOperation operation) {
        try {
            if (operation.operationType() == ActionDefinition.Type.START_DIALOGUE) {
                DialogueSession session = createDialogueSession(
                        operation.playerId(),
                        ContentId.parse(operation.payload().get("id")));
                scheduler.sync(() -> {
                    saveDialogue(session);
                    present(session);
                }).join();
                return new QuestGamePort.ActionResult(
                        QuestGamePort.ActionResult.Status.APPLIED, "");
            }
            if (operation.operationType() == ActionDefinition.Type.START_ENCOUNTER) {
                UUID instanceId = UUID.nameUUIDFromBytes(
                        ("quest-encounter:" + operation.operationId()).getBytes(
                                java.nio.charset.StandardCharsets.UTF_8));
                Set<UUID> participants = parties.party(operation.playerId())
                        .map(value -> value.members()).orElse(Set.of(operation.playerId()));
                Player player = plugin.getServer().getPlayer(operation.playerId());
                Location fallback = player == null
                        ? plugin.getServer().getWorlds().getFirst().getSpawnLocation()
                        : player.getLocation();
                Location arena = resolveLocation(
                        operation.payload().get("location"), fallback);
                encounterStarter.start(instanceId,
                        ContentId.parse(operation.payload().get("id")),
                        participants, arena).join();
                return new QuestGamePort.ActionResult(
                        QuestGamePort.ActionResult.Status.APPLIED, "");
            }
            scheduler.sync(() -> {
                switch (operation.operationType()) {
                    case START_CUTSCENE -> startCutscene(operation.playerId(),
                            ContentId.parse(operation.payload().get("id")));
                    case TELEPORT -> teleport(operation.playerId(),
                            operation.payload().get("location"));
                    case PLAY_SOUND -> sound(operation.playerId(),
                            operation.payload().get("sound"));
                    case SPAWN_ACTOR -> spawn(UUID.nameUUIDFromBytes(
                                    operation.operationId().getBytes(
                                            java.nio.charset.StandardCharsets.UTF_8)),
                            ContentId.parse(operation.payload().get("id")),
                            operation.payload().get("location"), true);
                    case DESPAWN_ACTOR -> despawn(UUID.fromString(
                            operation.payload().get("actor")));
                    default -> { }
                }
            }).join();
            return new QuestGamePort.ActionResult(
                    QuestGamePort.ActionResult.Status.APPLIED, "");
        } catch (RuntimeException failure) {
            return new QuestGamePort.ActionResult(
                    QuestGamePort.ActionResult.Status.REJECTED, root(failure));
        }
    }

    private void startDialogue(UUID playerId, ContentId id) {
        scheduler.async(() -> createDialogueSession(playerId, id))
                .whenComplete((session, failure) -> scheduler.sync(() -> {
                    if (failure != null) {
                        plugin.getLogger().warning("Dialogue start failed: " + root(failure));
                        return;
                    }
                    saveDialogue(session);
                    present(session);
                }));
    }

    private DialogueSession createDialogueSession(UUID playerId, ContentId id) {
        DialogueDefinition definition = content.catalog().dialogue(id).orElseThrow();
        Map<ContentId, QuestProgress> progress = quests.active(playerId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        QuestProgress::questId, value -> value));
        return dialogues.start(definition, playerId,
                content.catalog().revision(), clock.now(),
                (player, conditions) -> conditions.stream().allMatch(condition ->
                        conditionEngine.evaluate(condition, player, progress,
                                game, clock.now()) == ConditionEngine.Result.TRUE),
                this::executeDialogueActions);
    }

    private void executeDialogueActions(
            UUID playerId, UUID sessionId, long sequence, List<ActionDefinition> actions) {
        actions.forEach(action -> scheduler.async(() -> {
            Map<String, String> payload = new java.util.HashMap<>(action.values());
            action.numbers().forEach((key, value) -> payload.put(key, value.toString()));
            payload.put("required", Boolean.toString(action.required()));
            return game.execute(new PendingQuestOperation(
                    "dialogue:" + sessionId + ':' + sequence + ':' + action.id(),
                    playerId, ContentId.parse("branz:dialogue_action"), action.type(),
                    payload, PendingQuestOperation.State.PENDING, 0, clock.now(), ""));
        }));
    }

    private void saveDialogue(DialogueSession session) {
        persistDialogue(session);
        dialogueSessions.put(session.sessionId(), session);
        playerDialogue.put(session.playerId(), session.sessionId());
        if (session.state() == DialogueSession.State.COMPLETE
                || session.state() == DialogueSession.State.CANCELLED) {
            playerDialogue.remove(session.playerId(), session.sessionId());
        }
    }

    private void present(DialogueSession session) {
        Player player = plugin.getServer().getPlayer(session.playerId());
        if (player == null || !player.isOnline()) return;
        DialogueDefinition definition =
                content.catalog().dialogue(session.dialogueId()).orElse(null);
        if (definition == null) return;
        if (session.state() == DialogueSession.State.COMPLETE
                || session.state() == DialogueSession.State.CANCELLED) {
            clear(session.playerId(), session.sessionId());
            return;
        }
        DialogueNode node = definition.nodes().get(session.currentNode());
        if (node.type() == DialogueNode.Type.LINE) {
            line(session.playerId(), node.speakerKey(), node.textKey(), node.portrait());
            if (definition.historyPolicy() != DialogueDefinition.HistoryPolicy.NONE) {
                scheduler.async(() -> sessionStore.append(new DialogueHistoryEntry(
                        session.playerId(), session.dialogueId(), session.sessionId(),
                        session.sequence(), node.id(), node.speakerKey(), node.textKey(),
                        "", clock.now())));
            }
        } else if (node.type() == DialogueNode.Type.CHOICE) {
            choices(session.playerId(), session.sessionId(), session.sequence(),
                    node.choices().stream().map(choice ->
                            new QuestAudience.ChoiceView(choice.id(), choice.textKey(),
                                    choice.conditions().isEmpty(),
                                    choice.disabledReasonKey())).toList());
        }
    }

    private void startCutscene(UUID playerId, ContentId id) {
        CutsceneDefinition definition = content.catalog().cutscene(id).orElseThrow();
        CutsceneEngine.Step step = cutscenes.prepare(
                definition, Set.of(playerId), clock.monotonicNanos(), clock.now());
        cutsceneSessions.put(step.session().sessionId(), step.session());
        executeCutscene(step.actions(), step.session());
        persistCutscene(step.session());
        telemetry.increment("cutscene.started");
    }

    private void executeCutscene(List<CutsceneAction> actions, CutsceneSession session) {
        for (CutsceneAction action : actions) {
            for (UUID playerId : session.participantSnapshot()) {
                switch (action.type()) {
                    case CAMERA_CUT -> attach(playerId, session.sessionId(),
                            action.values().getOrDefault("path", "current"));
                    case CAMERA_LOOK_AT -> lookAt(playerId,
                            action.values().get("location"),
                            (long) action.numbers().getOrDefault("duration_ms", 0d).doubleValue());
                    case CAMERA_RESTORE -> restore(playerId, session.sessionId());
                    case FREEZE_INPUT -> {
                        if (Boolean.parseBoolean(
                                action.values().getOrDefault("enabled", "false"))) {
                            frozen.add(playerId);
                        } else frozen.remove(playerId);
                    }
                    case INVULNERABILITY -> {
                        Player player = plugin.getServer().getPlayer(playerId);
                        if (player != null) player.setInvulnerable(Boolean.parseBoolean(
                                action.values().getOrDefault("enabled", "false")));
                    }
                    case PLAYER_TELEPORT -> teleport(playerId, action.values().get("location"));
                    case SOUND -> sound(playerId, action.values().get("sound"));
                    case DIALOGUE -> startDialogue(playerId,
                            ContentId.parse(action.values().get("id")));
                    case ACTOR_SPAWN -> {
                        UUID actor = spawn(session.sessionId(),
                                ContentId.parse(action.values().get("id")),
                                action.values().getOrDefault("location", "player"), true);
                        actors.put(actor, actor);
                        sessionActors.computeIfAbsent(session.sessionId(),
                                ignored -> ConcurrentHashMap.newKeySet()).add(actor);
                    }
                    case ACTOR_DESPAWN -> {
                        String actor = action.values().get("actor");
                        if (actor != null) despawn(UUID.fromString(actor));
                    }
                    case ACTOR_SPEAK -> subtitle(playerId,
                            action.values().getOrDefault("text", ""));
                    case BLOCK_CHANGE -> applyPrivateBlockChange(
                            playerId, session.sessionId(), action);
                    case PARTICLE -> {
                        Player player = plugin.getServer().getPlayer(playerId);
                        AccessibilitySettings settings = accessibility.getOrDefault(
                                playerId, AccessibilitySettings.defaults(playerId));
                        if (player != null && settings.vfxIntensity()
                                != AccessibilitySettings.Intensity.OFF) {
                            player.spawnParticle(org.bukkit.Particle.END_ROD,
                                    player.getLocation().add(0, 1, 0),
                                    settings.vfxIntensity()
                                            == AccessibilitySettings.Intensity.LOW ? 3 : 10);
                        }
                    }
                    default -> { }
                }
            }
        }
    }

    private void cleanupCutscene(
            CutsceneDefinition definition, CutsceneSession session) {
        CutsceneEngine.Step cleanup = cutscenes.beginCleanup(
                definition, session, clock.monotonicNanos(), clock.now());
        executeCutscene(cleanup.actions(), cleanup.session());
        cleanup.session().participantSnapshot().forEach(player -> {
            restore(player, cleanup.session().sessionId());
            frozen.remove(player);
            Player online = plugin.getServer().getPlayer(player);
            if (online != null) online.setInvulnerable(false);
        });
        cleanup.session().actorIds().forEach(this::despawn);
        Optional.ofNullable(sessionActors.remove(cleanup.session().sessionId()))
                .orElse(Set.of()).forEach(this::despawn);
        restorePrivateBlocks(cleanup.session().sessionId());
        CutsceneSession complete =
                cutscenes.cleanupComplete(cleanup.session(), clock.now());
        persistCutscene(complete);
        cutsceneSessions.put(complete.sessionId(), complete);
    }

    private void recover() {
        scheduler.async(() -> new Recovery(
                sessionStore.recoverable(), sessionStore.recoverableCutscenes(),
                worldStore.locations(), worldStore.worldObjects()))
                .whenComplete((recovery, failure) -> scheduler.sync(() -> {
                    if (failure != null) {
                        plugin.getLogger().warning("Quest session recovery failed: "
                                + root(failure));
                        return;
                    }
                    recovery.locations().forEach(value -> {
                        org.bukkit.World world = plugin.getServer().getWorld(value.worldId());
                        if (world != null) {
                            locations.put(value.id(), new Location(world, value.x(), value.y(),
                                    value.z(), value.yaw(), value.pitch()));
                        }
                    });
                    recovery.worldObjects().forEach(value -> worldObjects.put(
                            value.worldId() + ":" + value.x() + ":" + value.y() + ":"
                                    + value.z(), value.objectId()));
                    recovery.dialogues().forEach(this::saveDialogue);
                    recovery.cutscenes().forEach(session -> {
                        cutsceneSessions.put(session.sessionId(), session);
                        CutsceneDefinition definition =
                                content.catalog().cutscene(session.cutsceneId()).orElse(null);
                        if (definition != null) {
                            CutsceneEngine.Step step = cutscenes.disconnect(
                                    definition, session, clock.monotonicNanos(), clock.now());
                            executeCutscene(step.actions(), step.session());
                            persistCutscene(step.session());
                            cutsceneSessions.put(session.sessionId(), step.session());
                            if (step.session().state() != CutsceneSession.State.PAUSED) {
                                cleanupCutscene(definition, step.session());
                            }
                        }
                    });
                }));
    }

    @Override public void line(
            UUID playerId, String speakerKey, String textKey, String portrait) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return;
        player.sendMessage(Component.text("[" + translate(speakerKey) + "] ",
                        NamedTextColor.GOLD)
                .append(Component.text(translate(textKey), NamedTextColor.WHITE)));
        AccessibilitySettings settings = accessibility.getOrDefault(
                playerId, AccessibilitySettings.defaults(playerId));
        if (settings.dialogueMode() == AccessibilitySettings.DialogueMode.MANUAL) {
            player.sendActionBar(Component.text("Sneak to continue", NamedTextColor.GRAY));
        }
    }

    @Override public void choices(
            UUID playerId, UUID sessionId, long sequence,
            List<QuestAudience.ChoiceView> choices) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return;
        choices.forEach(choice -> {
            Component line = Component.text(" • " + translate(choice.textKey()),
                    choice.enabled() ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY);
            if (choice.enabled()) {
                line = line.clickEvent(ClickEvent.runCommand(
                        "/branz dialogue choose " + sessionId + " "
                                + sequence + " " + choice.id()));
            } else {
                line = line.append(Component.text(
                        " (" + translate(choice.disabledReasonKey()) + ")"));
            }
            player.sendMessage(line);
        });
    }

    @Override public void tracker(
            UUID playerId, String titleKey, List<String> objectiveLines) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return;
        player.sendActionBar(Component.text(translate(titleKey) + ": "
                + String.join(", ", objectiveLines)));
    }
    @Override public void journal(UUID playerId, List<JournalEntry> entries) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return;
        player.sendMessage(Component.text("Quest Journal", NamedTextColor.GOLD));
        entries.forEach(entry -> player.sendMessage(Component.text(
                entry.titleKey() + " — " + entry.state() + " / " + entry.stage())));
    }
    @Override public void subtitle(UUID playerId, String textKey) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) player.sendActionBar(Component.text(translate(textKey)));
    }
    @Override public void sound(UUID playerId, String soundKey) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return;
        try {
            player.playSound(player.getLocation(), Sound.valueOf(
                    soundKey.toUpperCase(java.util.Locale.ROOT)), 1, 1);
        } catch (RuntimeException invalid) {
            if (accessibility.getOrDefault(playerId,
                    AccessibilitySettings.defaults(playerId)).soundAlternatives()) {
                player.sendActionBar(Component.text("[Sound: " + soundKey + "]"));
            }
        }
    }
    @Override public void clear(UUID playerId, UUID sessionId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) player.sendActionBar(Component.empty());
    }

    @Override public UUID spawn(
            UUID sessionId, ContentId actorDefinition,
            String locationId, boolean privateActor) {
        Location location = resolveLocation(locationId,
                plugin.getServer().getOnlinePlayers().stream().findFirst()
                        .map(Player::getLocation).orElseThrow());
        ArmorStand actor = location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(false);
            stand.setInvulnerable(true);
            stand.setGravity(false);
            stand.customName(Component.text(actorDefinition.value()));
            stand.setCustomNameVisible(true);
            stand.getPersistentDataContainer().set(actorSessionKey,
                    PersistentDataType.STRING, sessionId.toString());
        });
        if (privateActor) {
            Set<UUID> viewers = Optional.ofNullable(cutsceneSessions.get(sessionId))
                    .map(CutsceneSession::participantSnapshot).orElse(Set.of());
            privateActorViewers.put(actor.getUniqueId(), viewers);
            plugin.getServer().getOnlinePlayers().stream()
                    .filter(player -> !viewers.contains(player.getUniqueId()))
                    .forEach(player -> player.hideEntity(plugin, actor));
        }
        actors.put(actor.getUniqueId(), actor.getUniqueId());
        return actor.getUniqueId();
    }
    @Override public void despawn(UUID actorId) {
        Entity entity = plugin.getServer().getEntity(actorId);
        if (entity != null) entity.remove();
        actors.remove(actorId);
        privateActorViewers.remove(actorId);
    }
    @Override public Optional<ActorView> actor(UUID actorId) {
        Entity entity = plugin.getServer().getEntity(actorId);
        return entity == null ? Optional.empty() : Optional.of(new ActorView(
                actorId, ContentId.parse("branz:quest_actor"), "runtime", !entity.isDead()));
    }

    @Override public void attach(UUID playerId, UUID sessionId, String cameraPathId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return;
        cameraModes.putIfAbsent(playerId, player.getGameMode());
        player.setGameMode(GameMode.SPECTATOR);
    }
    @Override public void lookAt(UUID playerId, String locationId, long durationMillis) {
        Player player = plugin.getServer().getPlayer(playerId);
        Location target = locations.get(locationId);
        if (player != null && target != null) {
            Location from = player.getLocation();
            from.setDirection(target.toVector().subtract(from.toVector()));
            player.teleport(from);
        }
    }
    @Override public void restore(UUID playerId, UUID sessionId) {
        Player player = plugin.getServer().getPlayer(playerId);
        GameMode mode = cameraModes.remove(playerId);
        if (player != null && mode != null) {
            player.setSpectatorTarget(null);
            player.setGameMode(mode);
        }
    }

    @Override public boolean exists(String locationId) {
        return "player".equals(locationId) || locations.containsKey(locationId);
    }
    @Override public boolean teleport(UUID playerId, String locationId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return false;
        Location target = resolveLocation(locationId, player.getLocation());
        return player.teleport(target);
    }
    @Override public Optional<String> currentRegion(UUID playerId) { return Optional.empty(); }
    @Override public double distance(UUID playerId, String locationId) {
        Player player = plugin.getServer().getPlayer(playerId);
        Location target = locations.get(locationId);
        if (player == null || target == null || !player.getWorld().equals(target.getWorld())) {
            return Double.POSITIVE_INFINITY;
        }
        return player.getLocation().distance(target);
    }
    public void captureLocation(String id, Location location) {
        locations.put(id, location.clone());
        JdbcQuestWorldStore.LocationRecord record =
                new JdbcQuestWorldStore.LocationRecord(id, location.getWorld().getUID(),
                        location.getX(), location.getY(), location.getZ(),
                        location.getYaw(), location.getPitch());
        scheduler.async(() -> worldStore.save(record));
    }

    public Map<String, Location> locations() {
        return Map.copyOf(locations);
    }

    public boolean deleteLocation(String id) {
        boolean existed = locations.remove(id) != null;
        scheduler.async(() -> worldStore.deleteLocation(id));
        return existed;
    }

    public void accessibility(
            UUID playerId, AccessibilitySettings.DialogueMode mode, double textSpeed,
            boolean soundAlternatives, Consumer<String> feedback) {
        AccessibilitySettings before = accessibility.getOrDefault(
                playerId, AccessibilitySettings.defaults(playerId));
        AccessibilitySettings next = new AccessibilitySettings(playerId, mode, textSpeed,
                before.skipPreviouslyRead(), before.portraitIntensity(),
                before.vfxIntensity(), soundAlternatives);
        accessibility.put(playerId, next);
        scheduler.async(() -> sessionStore.save(next)).whenComplete((saved, failure) ->
                scheduler.sync(() -> feedback.accept(failure == null
                        ? "Accessibility settings saved."
                        : "Accessibility save failed: " + root(failure))));
    }

    private Location resolveLocation(String id, Location fallback) {
        return id == null || "player".equals(id)
                ? fallback.clone() : Optional.ofNullable(locations.get(id))
                .map(Location::clone).orElseThrow(
                        () -> new IllegalArgumentException("unknown location " + id));
    }

    private void applyPrivateBlockChange(
            UUID playerId, UUID sessionId, CutsceneAction action) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return;
        Location location = resolveLocation(
                action.values().get("location"), player.getLocation());
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(
                action.values().getOrDefault("block", "AIR"));
        if (material == null || !material.isBlock()) return;
        String key = blockKey(location);
        privateBlockChanges.computeIfAbsent(sessionId,
                ignored -> new ConcurrentHashMap<>()).putIfAbsent(
                key, location.getBlock().getBlockData());
        player.sendBlockChange(location, material.createBlockData());
    }

    private void restorePrivateBlocks(UUID sessionId) {
        Map<String, org.bukkit.block.data.BlockData> changes =
                privateBlockChanges.remove(sessionId);
        if (changes == null) return;
        CutsceneSession session = cutsceneSessions.get(sessionId);
        Set<UUID> viewers = session == null ? Set.of() : session.participantSnapshot();
        changes.forEach((key, original) -> {
            Location location = parseBlockKey(key);
            if (location == null) return;
            viewers.forEach(playerId -> {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) player.sendBlockChange(location, original);
            });
        });
    }

    private Location parseBlockKey(String key) {
        String[] parts = key.split(":", 4);
        if (parts.length != 4) return null;
        try {
            org.bukkit.World world = plugin.getServer().getWorld(UUID.fromString(parts[0]));
            return world == null ? null : new Location(world,
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private void cleanupOrphanActors() {
        plugin.getServer().getWorlds().forEach(world -> world.getEntities().stream()
                .filter(entity -> entity.getPersistentDataContainer().has(
                        actorSessionKey, PersistentDataType.STRING))
                .forEach(Entity::remove));
    }

    private static String root(Throwable failure) {
        Throwable value = failure;
        while (value.getCause() != null) value = value.getCause();
        return value.getMessage();
    }

    private String translate(String key) {
        if (key == null || key.isBlank()) return "";
        return localization.getOrDefault(key, key);
    }

    private void loadLocalization() {
        java.util.HashMap<String, String> loaded = new java.util.HashMap<>();
        Path english = contentDirectory.resolve("lang").resolve("en_us.yml");
        if (java.nio.file.Files.isRegularFile(english)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(english.toFile());
            for (String key : yaml.getKeys(true)) {
                if (yaml.isString(key)) loaded.put(key, yaml.getString(key, key));
            }
        }
        localization = Map.copyOf(loaded);
    }

    private void validateLocalization() {
        java.util.HashSet<String> required = new java.util.HashSet<>();
        content.catalog().quests().values().forEach(quest -> {
            required.add(quest.titleKey());
            required.add(quest.descriptionKey());
        });
        content.catalog().dialogues().values().forEach(dialogue ->
                dialogue.nodes().values().forEach(node -> {
                    if (!node.speakerKey().isBlank()) required.add(node.speakerKey());
                    if (!node.textKey().isBlank()) required.add(node.textKey());
                    node.choices().forEach(choice -> required.add(choice.textKey()));
                }));
        content.catalog().cutscenes().values().forEach(scene ->
                java.util.stream.Stream.of(scene.setup(), scene.timeline(),
                                scene.finalState(), scene.skipState(), scene.cleanup())
                        .flatMap(List::stream)
                        .map(action -> action.values().get("text"))
                        .filter(java.util.Objects::nonNull).forEach(required::add));
        required.removeIf(localization::containsKey);
        if (!required.isEmpty()) {
            throw new IllegalStateException(
                    "missing en_us localization keys: " + required);
        }
    }

    private static String blockKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX()
                + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private void persistDialogue(DialogueSession session) {
        scheduler.async(() -> sessionStore.save(session)).exceptionally(failure -> {
            plugin.getLogger().warning("Dialogue persistence failed: " + root(failure));
            return null;
        });
    }

    private void persistCutscene(CutsceneSession session) {
        scheduler.async(() -> sessionStore.saveCutscene(session)).exceptionally(failure -> {
            plugin.getLogger().warning("Cutscene persistence failed: " + root(failure));
            return null;
        });
    }

    private record Recovery(Collection<DialogueSession> dialogues,
                            Collection<CutsceneSession> cutscenes,
                            Collection<JdbcQuestWorldStore.LocationRecord> locations,
                            Collection<JdbcQuestWorldStore.WorldObjectRecord> worldObjects) {
    }

    private record MemberPosition(UUID worldId, double x, double y, double z) {
    }
}
