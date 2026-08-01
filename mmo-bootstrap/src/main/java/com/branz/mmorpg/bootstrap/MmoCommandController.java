package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.move.MoveEngine;
import com.branz.mmorpg.combat.resource.BossFlaskCheckpointEngine;
import com.branz.mmorpg.combat.resource.ExpeditionFlaskEngine;
import com.branz.mmorpg.combat.resource.FlaskAllocation;
import com.branz.mmorpg.combat.resource.FlaskCheckpointErrorCode;
import com.branz.mmorpg.combat.resource.FlaskConsumption;
import com.branz.mmorpg.combat.resource.FlaskDose;
import com.branz.mmorpg.combat.resource.FlaskErrorCode;
import com.branz.mmorpg.combat.resource.FlaskState;
import com.branz.mmorpg.combat.resource.PreparedFlaskSnapshot;
import com.branz.mmorpg.combat.status.AilmentDefinition;
import com.branz.mmorpg.combat.status.AilmentDefinitionEngine;
import com.branz.mmorpg.combat.status.AilmentEngine;
import com.branz.mmorpg.combat.status.AilmentState;
import com.branz.mmorpg.combat.status.AilmentType;
import com.branz.mmorpg.combat.trace.CombatSimulationErrorCode;
import com.branz.mmorpg.combat.trace.CombatTrace;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.items.consumable.ActiveConsumableEffect;
import com.branz.mmorpg.items.consumable.ConsumableCategory;
import com.branz.mmorpg.items.consumable.ConsumableEffectEngine;
import com.branz.mmorpg.items.consumable.ConsumableEffectErrorCode;
import com.branz.mmorpg.items.consumable.ConsumableEffectState;
import com.branz.mmorpg.items.consumable.ConsumableUseEngine;
import com.branz.mmorpg.items.consumable.ConsumableUseProfile;
import com.branz.mmorpg.items.consumable.ConsumableUseState;
import com.branz.mmorpg.items.consumable.ConsumableUseTransition;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.persistence.progression.ProgressionEvidenceExecution;
import com.branz.mmorpg.persistence.progression.ProgressionTrackRecord;
import com.branz.mmorpg.persistence.progression.TeachingCommitRequest;
import com.branz.mmorpg.progression.evidence.EncounterOutcome;
import com.branz.mmorpg.progression.evidence.EvidenceCandidate;
import com.branz.mmorpg.progression.evidence.EvidenceContext;
import com.branz.mmorpg.progression.evidence.EvidenceDecision;
import com.branz.mmorpg.progression.evidence.EvidenceTargetKind;
import com.branz.mmorpg.progression.evidence.ProgressionEvidenceEngine;
import com.branz.mmorpg.progression.evidence.ProgressionTrack;
import com.branz.mmorpg.progression.evidence.ReadinessBand;
import com.branz.mmorpg.progression.knowledge.KnowledgeKey;
import com.branz.mmorpg.progression.knowledge.KnowledgeProfile;
import com.branz.mmorpg.progression.knowledge.KnowledgeType;
import com.branz.mmorpg.progression.knowledge.LearningRequirements;
import com.branz.mmorpg.progression.renown.RenownContext;
import com.branz.mmorpg.progression.renown.RenownDecision;
import com.branz.mmorpg.progression.renown.RenownDeedCandidate;
import com.branz.mmorpg.progression.renown.RenownEngine;
import com.branz.mmorpg.progression.teaching.TeachingCompletion;
import com.branz.mmorpg.progression.teaching.TeachingErrorCode;
import com.branz.mmorpg.progression.teaching.TeachingSession;
import com.branz.mmorpg.progression.teaching.TeachingSessionEngine;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

final class MmoCommandController implements CommandExecutor, Listener {
    private static final List<DevModule> MODULES =
            List.of(
                    new DevModule(10, Material.COMPASS, "Content Browser", "content"),
                    new DevModule(12, Material.BARRIER, "Persisted Test Item", "spawner"),
                    new DevModule(14, Material.PLAYER_HEAD, "Character Profile (locked)", "locked"),
                    new DevModule(16, Material.IRON_SWORD, "Training Move Tester", "combat"),
                    new DevModule(
                            22,
                            Material.EXPERIENCE_BOTTLE,
                            "Progression Evidence Lab",
                            "progression"),
                    new DevModule(24, Material.WRITABLE_BOOK, "Teaching & Renown Lab", "teaching"),
                    new DevModule(28, Material.ZOMBIE_HEAD, "Boss Encounter Lab", "encounter"),
                    new DevModule(30, Material.PAINTING, "Scene/UI Tester", "scene"),
                    new DevModule(
                            32,
                            Material.DIAMOND_PICKAXE,
                            "Resource Node Tester (locked)",
                            "locked"),
                    new DevModule(34, Material.CRAFTING_TABLE, "Recipe Tester (locked)", "locked"),
                    new DevModule(49, Material.BARRIER, "Close", "close"));

    private final JavaPlugin plugin;
    private final BootstrapLifecycle lifecycle;
    private final ResourcePackGate packGate;
    private final Supplier<ContentSnapshot> snapshotSource;
    private final Supplier<ItemEngine> itemEngineSource;
    private final Supplier<MoveEngine> moveEngineSource;
    private final Supplier<AilmentDefinitionEngine> ailmentEngineSource;
    private final SceneHubController sceneHub;
    private final CharacterSessionController characterSessions;
    private final CombatSessionController combatSessions;
    private final LiveTeachingSessionController liveTeaching;
    private final KnowledgeAcquisitionController knowledgeAcquisition;
    private final BossEncounterController bossEncounters;
    private final DeathPouchController deathPouches;
    private final PartyController parties;
    private final LfgController lfg;
    private final DownedController downed;
    private final PvpController pvp;
    private final CombatTraceFileExporter traceExporter;
    private final ProgressionEvidenceEngine progressionEvidence = new ProgressionEvidenceEngine();
    private final TeachingSessionEngine teachingEngine = new TeachingSessionEngine();
    private final RenownEngine renownEngine = new RenownEngine();
    private final NamespacedKey actionKey;

    MmoCommandController(
            JavaPlugin plugin,
            BootstrapLifecycle lifecycle,
            ResourcePackGate packGate,
            Supplier<ContentSnapshot> snapshotSource,
            Supplier<ItemEngine> itemEngineSource,
            Supplier<MoveEngine> moveEngineSource,
            Supplier<AilmentDefinitionEngine> ailmentEngineSource,
            SceneHubController sceneHub,
            CharacterSessionController characterSessions,
            CombatSessionController combatSessions,
            LiveTeachingSessionController liveTeaching,
            KnowledgeAcquisitionController knowledgeAcquisition,
            BossEncounterController bossEncounters,
            DeathPouchController deathPouches,
            PartyController parties,
            LfgController lfg,
            DownedController downed,
            PvpController pvp) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.packGate = Objects.requireNonNull(packGate, "packGate");
        this.snapshotSource = Objects.requireNonNull(snapshotSource, "snapshotSource");
        this.itemEngineSource = Objects.requireNonNull(itemEngineSource, "itemEngineSource");
        this.moveEngineSource = Objects.requireNonNull(moveEngineSource, "moveEngineSource");
        this.ailmentEngineSource =
                Objects.requireNonNull(ailmentEngineSource, "ailmentEngineSource");
        this.sceneHub = Objects.requireNonNull(sceneHub, "sceneHub");
        this.characterSessions = Objects.requireNonNull(characterSessions, "characterSessions");
        this.combatSessions = Objects.requireNonNull(combatSessions, "combatSessions");
        this.liveTeaching = Objects.requireNonNull(liveTeaching, "liveTeaching");
        this.knowledgeAcquisition =
                Objects.requireNonNull(knowledgeAcquisition, "knowledgeAcquisition");
        this.bossEncounters = Objects.requireNonNull(bossEncounters, "bossEncounters");
        this.deathPouches = Objects.requireNonNull(deathPouches, "deathPouches");
        this.parties = Objects.requireNonNull(parties, "parties");
        this.lfg = Objects.requireNonNull(lfg, "lfg");
        this.downed = Objects.requireNonNull(downed, "downed");
        this.pvp = Objects.requireNonNull(pvp, "pvp");
        traceExporter =
                new CombatTraceFileExporter(
                        plugin.getDataFolder().toPath().resolve("combat-traces"));
        actionKey = new NamespacedKey(plugin, "dev_action");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0 || "health".equalsIgnoreCase(args[0])) {
            showHealth(sender);
            return true;
        }
        if ("dev".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("The in-game dev console requires a player.");
                return true;
            }
            openDevHub(player);
            return true;
        }
        if ("combat".equalsIgnoreCase(args[0])) {
            handleCombatTool(sender, args);
            return true;
        }
        if ("progression".equalsIgnoreCase(args[0])) {
            handleProgressionTool(sender, args);
            return true;
        }
        if ("teaching".equalsIgnoreCase(args[0])) {
            handleTeachingTool(sender, args);
            return true;
        }
        if ("renown".equalsIgnoreCase(args[0])) {
            handleRenownTool(sender, args);
            return true;
        }
        if ("knowledge".equalsIgnoreCase(args[0])) {
            handleKnowledgeTool(sender, args);
            return true;
        }
        if ("consumable".equalsIgnoreCase(args[0])) {
            handleConsumableTool(sender, args);
            return true;
        }
        if ("encounter".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Encounter Lab requires an in-game player.");
            } else if (!devToolsAllowed(player)) {
                player.sendMessage(
                        Component.text(
                                "Encounter Lab is disabled for this environment/account.",
                                NamedTextColor.RED));
            } else {
                bossEncounters.handleCommand(player, args);
            }
            return true;
        }
        if ("party".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Party commands require an in-game player.");
            } else {
                parties.handleCommand(player, args);
            }
            return true;
        }
        if ("pouch".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Death Pouch commands require an in-game player.");
            } else {
                deathPouches.handleCommand(player, args, devToolsAllowed(player));
            }
            return true;
        }
        if ("lfg".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("LFG commands require an in-game player.");
            } else {
                lfg.handleCommand(player, args);
            }
            return true;
        }
        if ("downed".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Downed Lab requires an in-game player.");
            } else if (!devToolsAllowed(player)) {
                player.sendMessage(
                        Component.text(
                                "Downed Lab is disabled for this environment/account.",
                                NamedTextColor.RED));
            } else {
                downed.handleCommand(player, args);
            }
            return true;
        }
        if ("pvp".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("PvP Lab requires an in-game player.");
            } else if (!devToolsAllowed(player)) {
                player.sendMessage(
                        Component.text(
                                "PvP Lab is disabled for this environment/account.",
                                NamedTextColor.RED));
            } else {
                pvp.handleCommand(player, args);
            }
            return true;
        }
        sender.sendMessage(
                "Usage: /mmo <health|dev|combat|progression|teaching|renown|knowledge|consumable|party|lfg|encounter|downed|pouch|pvp>");
        return true;
    }

    private void handleConsumableTool(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Consumable Lab requires an in-game player.");
            return;
        }
        if (!devToolsAllowed(player)) {
            player.sendMessage(
                    Component.text(
                            "Consumable Lab is disabled for this environment/account.",
                            NamedTextColor.RED));
            return;
        }
        if (args.length == 2 && "status".equalsIgnoreCase(args[1])) {
            showConsumableStatus(player);
            return;
        }
        if (args.length >= 2 && "persist".equalsIgnoreCase(args[1])) {
            UUID operationId = parseUuid(player, args, 2, "Expedition operation");
            if (operationId != null) {
                persistConsumableFixture(player, operationId);
            }
            return;
        }
        if (args.length >= 4 && "checkpoint".equalsIgnoreCase(args[1])) {
            UUID checkpointId = parseUuid(player, args, 3, "Checkpoint instance");
            UUID operationId = parseUuid(player, args, 4, "Expedition operation");
            if (checkpointId == null || operationId == null) {
                return;
            }
            if ("capture".equalsIgnoreCase(args[2])) {
                captureFlaskCheckpoint(player, checkpointId, operationId);
            } else if ("restore".equalsIgnoreCase(args[2])) {
                restoreFlaskCheckpoint(player, checkpointId, operationId);
            } else {
                consumableUsage(player);
            }
            return;
        }
        if (args.length != 3 || !"simulate".equalsIgnoreCase(args[1])) {
            consumableUsage(player);
            return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "flask" -> simulateFlask(player);
            case "timeline" -> simulateConsumableTimeline(player);
            case "ailment" -> simulateAilment(player);
            case "category" -> simulateConsumableCategory(player);
            default -> consumableUsage(player);
        }
    }

    private static void simulateFlask(Player player) {
        Result<FlaskConsumption, FlaskErrorCode> result =
                new ExpeditionFlaskEngine()
                        .consume(FlaskState.full(FlaskAllocation.balanced()), FlaskDose.HEALING);
        FlaskConsumption consumption =
                ((Result.Success<FlaskConsumption, FlaskErrorCode>) result).value();
        player.sendMessage(
                Component.text(
                        "Flask Lab: COMMITTED | charges="
                                + consumption.state().totalCharges()
                                + "/5 | maximum-health="
                                + consumption.restoration().maximumHealthRatio(),
                        NamedTextColor.GREEN));
    }

    private static void simulateConsumableTimeline(Player player) {
        ConsumableUseState started =
                ConsumableUseState.start(
                        UUID.randomUUID(),
                        DefinitionId.of("consumable.expedition_flask"),
                        ConsumableUseProfile.expeditionFlask(),
                        100);
        ConsumableUseTransition transition = new ConsumableUseEngine().advance(started, 118, true);
        player.sendMessage(
                Component.text(
                        "Consumable Timeline Lab: phase="
                                + transition.state().phase()
                                + " | commit-now="
                                + transition.commitNow()
                                + " | consumed="
                                + transition.state().consumed(),
                        NamedTextColor.GREEN));
    }

    private void simulateAilment(Player player) {
        AilmentDefinitionEngine definitions = ailmentEngineSource.get();
        AilmentDefinition burn =
                definitions == null ? null : definitions.find(AilmentType.BURN).orElse(null);
        if (burn == null) {
            player.sendMessage(
                    Component.text(
                            "Ailment Lab: authored Burn definition is unavailable.",
                            NamedTextColor.RED));
            return;
        }
        AilmentState active =
                new AilmentEngine()
                        .applyBuildup(burn, AilmentState.empty(100), 100, 0, 100)
                        .state();
        player.sendMessage(
                Component.text(
                        "Ailment Lab: BURN active="
                                + active.activeAt(100)
                                + " | tier="
                                + active.tier()
                                + " | buildup="
                                + active.buildup(),
                        NamedTextColor.GREEN));
    }

    private static void simulateConsumableCategory(Player player) {
        ConsumableEffectEngine engine = new ConsumableEffectEngine();
        ConsumableEffectState rare =
                ((Result.Success<ConsumableEffectState, ConsumableEffectErrorCode>)
                                engine.apply(
                                        ConsumableEffectState.empty(),
                                        new ActiveConsumableEffect(
                                                DefinitionId.of("effect.tonic.rare"),
                                                ConsumableCategory.BODY_TONIC,
                                                500,
                                                true),
                                        100,
                                        false))
                        .value();
        Result<ConsumableEffectState, ConsumableEffectErrorCode> replacement =
                engine.apply(
                        rare,
                        new ActiveConsumableEffect(
                                DefinitionId.of("effect.tonic.common"),
                                ConsumableCategory.BODY_TONIC,
                                500,
                                false),
                        100,
                        false);
        Result.Failure<ConsumableEffectState, ConsumableEffectErrorCode> failure =
                (Result.Failure<ConsumableEffectState, ConsumableEffectErrorCode>) replacement;
        player.sendMessage(
                Component.text(
                        "Consumable Category Lab: " + failure.error().code(),
                        NamedTextColor.YELLOW));
    }

    private void showConsumableStatus(Player player) {
        LoadedCharacterSession session = characterSessions.active(player).orElse(null);
        if (session == null || !characterSessions.ready(player)) {
            player.sendMessage(
                    Component.text("Character session is not ready.", NamedTextColor.RED));
            return;
        }
        PersistentCharacterSnapshot snapshot = session.snapshot();
        PersistentExpeditionState state = snapshot.expeditionState();
        long version = snapshot.expeditionStateRecord().map(record -> record.version()).orElse(0L);
        player.sendMessage(
                Component.text(
                        "Expedition State: version="
                                + version
                                + " | Flask="
                                + state.flaskState().totalCharges()
                                + "/"
                                + state.flaskState().allocation().capacity()
                                + " | effects="
                                + state.consumableEffects().size()
                                + " | ailments="
                                + state.ailments().size()
                                + " | checkpoint="
                                + state.preparedFlaskSnapshot()
                                        .map(value -> value.checkpointInstanceId().toString())
                                        .orElse("none"),
                        NamedTextColor.GOLD));
        state.consumableEffects()
                .forEach(
                        effect ->
                                player.sendMessage(
                                        Component.text(
                                                "Effect "
                                                        + effect.category()
                                                        + " "
                                                        + effect.definitionId().value()
                                                        + " | remaining="
                                                        + effect.remainingTicks()
                                                        + "t",
                                                NamedTextColor.AQUA)));
        state.ailments()
                .values()
                .forEach(
                        ailment ->
                                player.sendMessage(
                                        Component.text(
                                                "Ailment "
                                                        + ailment.type()
                                                        + " | buildup="
                                                        + ailment.buildup()
                                                        + " | active="
                                                        + ailment.activeRemainingTicks()
                                                        + "t | tier="
                                                        + ailment.tier(),
                                                NamedTextColor.LIGHT_PURPLE)));
    }

    private void captureFlaskCheckpoint(
            Player player, UUID checkpointInstanceId, UUID operationId) {
        LoadedCharacterSession session = readyCharacter(player);
        if (session == null) {
            return;
        }
        PersistentExpeditionState current = session.snapshot().expeditionState();
        PreparedFlaskSnapshot prepared =
                new BossFlaskCheckpointEngine().capture(checkpointInstanceId, current.flaskState());
        PersistentExpeditionState desired =
                new PersistentExpeditionState(
                        current.flaskState(),
                        current.consumableEffects(),
                        current.ailments(),
                        java.util.Optional.of(prepared));
        commitCheckpointFixture(player, desired, operationId, "captured", checkpointInstanceId);
    }

    private void restoreFlaskCheckpoint(
            Player player, UUID checkpointInstanceId, UUID operationId) {
        LoadedCharacterSession session = readyCharacter(player);
        if (session == null) {
            return;
        }
        PersistentExpeditionState current = session.snapshot().expeditionState();
        PreparedFlaskSnapshot prepared = current.preparedFlaskSnapshot().orElse(null);
        if (prepared == null) {
            player.sendMessage(
                    Component.text("No prepared boss Flask snapshot exists.", NamedTextColor.RED));
            return;
        }
        Result<FlaskState, FlaskCheckpointErrorCode> restored =
                new BossFlaskCheckpointEngine().restore(checkpointInstanceId, prepared, true);
        if (restored instanceof Result.Failure<FlaskState, FlaskCheckpointErrorCode> failure) {
            player.sendMessage(
                    Component.text(
                            failure.error().code() + ": " + failure.detail(), NamedTextColor.RED));
            return;
        }
        FlaskState restoredFlask =
                ((Result.Success<FlaskState, FlaskCheckpointErrorCode>) restored).value();
        PersistentExpeditionState desired =
                new PersistentExpeditionState(
                        restoredFlask,
                        current.consumableEffects(),
                        current.ailments(),
                        current.preparedFlaskSnapshot());
        commitCheckpointFixture(player, desired, operationId, "restored", checkpointInstanceId);
    }

    private LoadedCharacterSession readyCharacter(Player player) {
        LoadedCharacterSession session = characterSessions.active(player).orElse(null);
        if (session == null || !characterSessions.ready(player)) {
            player.sendMessage(
                    Component.text("Character session is not ready.", NamedTextColor.RED));
            return null;
        }
        return session;
    }

    private void commitCheckpointFixture(
            Player player,
            PersistentExpeditionState desired,
            UUID operationId,
            String action,
            UUID checkpointInstanceId) {
        ContentSnapshot snapshot = snapshotSource.get();
        if (snapshot == null) {
            player.sendMessage(
                    Component.text("Content snapshot is not ready.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(
                Component.text("Committing boss Flask checkpoint...", NamedTextColor.YELLOW));
        characterSessions.commitExpeditionState(
                player,
                desired,
                operationId,
                snapshot.manifest().contentVersion(),
                result -> {
                    if (result
                            instanceof
                            Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>
                                    failure) {
                        player.sendMessage(
                                Component.text(
                                        "Boss Flask checkpoint failed: "
                                                + failure.error().code()
                                                + " "
                                                + failure.detail(),
                                        NamedTextColor.RED));
                        return;
                    }
                    LoadedCharacterSession committed =
                            ((Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>)
                                            result)
                                    .value();
                    player.sendMessage(
                            Component.text(
                                    "Boss Flask checkpoint "
                                            + action
                                            + " | checkpoint="
                                            + checkpointInstanceId
                                            + " | version="
                                            + committed
                                                    .snapshot()
                                                    .expeditionStateRecord()
                                                    .orElseThrow()
                                                    .version()
                                            + " | operation="
                                            + operationId,
                                    NamedTextColor.GREEN));
                });
    }

    private void persistConsumableFixture(Player player, UUID operationId) {
        ContentSnapshot snapshot = snapshotSource.get();
        if (snapshot == null) {
            player.sendMessage(
                    Component.text("Content snapshot is not ready.", NamedTextColor.RED));
            return;
        }
        PersistentExpeditionState desired =
                new PersistentExpeditionState(
                        new FlaskState(
                                FlaskAllocation.balanced(),
                                Map.of(
                                        FlaskDose.HEALING,
                                        2,
                                        FlaskDose.MANA,
                                        1,
                                        FlaskDose.STAMINA,
                                        0)),
                        List.of(
                                new PersistentConsumableEffect(
                                        DefinitionId.of("consumable.training_body_tonic"),
                                        ConsumableCategory.BODY_TONIC,
                                        900,
                                        true)),
                        Map.of(
                                AilmentType.BURN,
                                new PersistentAilmentState(AilmentType.BURN, 45.5, 20, 0, 0),
                                AilmentType.CORRUPTION,
                                new PersistentAilmentState(AilmentType.CORRUPTION, 0, 0, 400, 2)));
        player.sendMessage(
                Component.text(
                        "Persisting expedition fixture through PostgreSQL…",
                        NamedTextColor.YELLOW));
        characterSessions.commitExpeditionState(
                player,
                desired,
                operationId,
                snapshot.manifest().contentVersion(),
                result -> {
                    if (result
                            instanceof
                            Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>
                                    failure) {
                        player.sendMessage(
                                Component.text(
                                        "Expedition state failed: "
                                                + failure.error().code()
                                                + " "
                                                + failure.detail(),
                                        NamedTextColor.RED));
                        return;
                    }
                    LoadedCharacterSession committed =
                            ((Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>)
                                            result)
                                    .value();
                    long version =
                            committed.snapshot().expeditionStateRecord().orElseThrow().version();
                    player.sendMessage(
                            Component.text(
                                    "Expedition state persisted at version "
                                            + version
                                            + " | operation="
                                            + operationId,
                                    NamedTextColor.GREEN));
                });
    }

    private void handleKnowledgeTool(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Knowledge Lab requires an in-game player.");
            return;
        }
        if (!devToolsAllowed(player)) {
            player.sendMessage(
                    Component.text(
                            "Knowledge Lab is disabled for this environment/account.",
                            NamedTextColor.RED));
            return;
        }
        if (args.length >= 4 && "acquire".equalsIgnoreCase(args[1])) {
            KnowledgeType type;
            try {
                type = KnowledgeType.valueOf(args[2].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                knowledgeUsage(player);
                return;
            }
            if (type != KnowledgeType.FORM && type != KnowledgeType.SPELL) {
                knowledgeUsage(player);
                return;
            }
            UUID acquisitionId = parseUuid(player, args, 4, "Acquisition");
            if (acquisitionId == null) {
                return;
            }
            try {
                knowledgeAcquisition.completeAuthoredFixture(
                        player, new KnowledgeKey(type, DefinitionId.of(args[3])), acquisitionId);
            } catch (IllegalArgumentException exception) {
                player.sendMessage(
                        Component.text("Knowledge definition ID is invalid.", NamedTextColor.RED));
            }
            return;
        }
        knowledgeUsage(player);
    }

    private void handleTeachingTool(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Teaching requires an in-game player.");
            return;
        }
        if (args.length == 2 && "session".equalsIgnoreCase(args[1])) {
            liveTeaching.showStatus(player);
            return;
        }
        if (args.length == 2 && "cancel".equalsIgnoreCase(args[1])) {
            liveTeaching.cancel(player);
            return;
        }
        if (args.length == 4 && "start".equalsIgnoreCase(args[1])) {
            Player student = Bukkit.getPlayerExact(args[2]);
            if (student == null) {
                player.sendMessage(Component.text("Student is not online.", NamedTextColor.RED));
                return;
            }
            try {
                liveTeaching.begin(player, student, DefinitionId.of(args[3]));
            } catch (IllegalArgumentException exception) {
                player.sendMessage(Component.text("Technique ID is invalid.", NamedTextColor.RED));
            }
            return;
        }
        if (!devToolsAllowed(player)) {
            player.sendMessage(
                    Component.text(
                            "Teaching Lab is disabled for this environment/account.",
                            NamedTextColor.RED));
            return;
        }
        if (args.length >= 2 && "status".equalsIgnoreCase(args[1])) {
            Player target = onlineTarget(player, args, 2);
            if (target != null) {
                showKnowledgeStatus(player, target);
            }
            return;
        }
        if (args.length == 3 && "simulate".equalsIgnoreCase(args[1])) {
            simulateTeaching(player, args[2].toLowerCase(Locale.ROOT));
            return;
        }
        if (args.length >= 3 && "record".equalsIgnoreCase(args[1])) {
            Player student = Bukkit.getPlayerExact(args[2]);
            if (student == null) {
                player.sendMessage(Component.text("Student is not online.", NamedTextColor.RED));
                return;
            }
            UUID teachingSessionId = parseUuid(player, args, 3, "Teaching session");
            if (teachingSessionId == null) {
                return;
            }
            UUID deedId = parseUuid(player, args, 4, "Renown deed");
            if (deedId == null) {
                return;
            }
            recordTeaching(player, student, teachingSessionId, deedId);
            return;
        }
        teachingUsage(player);
    }

    private void handleRenownTool(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Renown Lab requires an in-game player.");
            return;
        }
        if (!devToolsAllowed(player)) {
            player.sendMessage(
                    Component.text(
                            "Renown Lab is disabled for this environment/account.",
                            NamedTextColor.RED));
            return;
        }
        if (args.length == 3 && "simulate".equalsIgnoreCase(args[1])) {
            simulateRenown(player, args[2].toLowerCase(Locale.ROOT));
            return;
        }
        renownUsage(player);
    }

    private void handleProgressionTool(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Progression inspection requires an in-game player.");
            return;
        }
        if (!devToolsAllowed(player)) {
            player.sendMessage(
                    Component.text(
                            "Progression inspection is disabled for this environment/account.",
                            NamedTextColor.RED));
            return;
        }
        if (args.length >= 2 && "status".equalsIgnoreCase(args[1])) {
            showProgressionStatus(player);
            return;
        }
        if (args.length < 3) {
            progressionUsage(player);
            return;
        }
        String scenario = args[2].toLowerCase(Locale.ROOT);
        if ("simulate".equalsIgnoreCase(args[1])) {
            simulateProgressionEvidence(player, scenario);
            return;
        }
        if ("record".equalsIgnoreCase(args[1])) {
            UUID evidenceId;
            try {
                evidenceId = args.length >= 4 ? UUID.fromString(args[3]) : UUID.randomUUID();
            } catch (IllegalArgumentException exception) {
                player.sendMessage(Component.text("Evidence UUID is invalid.", NamedTextColor.RED));
                return;
            }
            recordProgressionEvidence(player, scenario, evidenceId);
            return;
        }
        progressionUsage(player);
    }

    private void handleCombatTool(CommandSender sender, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("Combat inspection requires an in-game player viewer.");
            return;
        }
        if (!devToolsAllowed(viewer)) {
            sender.sendMessage(
                    Component.text(
                            "Combat inspection is disabled for this environment/account.",
                            NamedTextColor.RED));
            return;
        }
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[1])) {
            Player target = onlineTarget(viewer, args, 2);
            if (target == null) {
                return;
            }
            combatSessions
                    .toggleDebug(viewer, target)
                    .ifPresentOrElse(
                            enabled ->
                                    viewer.sendMessage(
                                            Component.text(
                                                    "Combat ARC debug "
                                                            + (enabled ? "enabled" : "disabled")
                                                            + " for "
                                                            + target.getName()
                                                            + ".",
                                                    enabled
                                                            ? NamedTextColor.GREEN
                                                            : NamedTextColor.GRAY)),
                            () ->
                                    viewer.sendMessage(
                                            Component.text(
                                                    "Target combat session is not ready.",
                                                    NamedTextColor.RED)));
            return;
        }
        if (args.length >= 3
                && "trace".equalsIgnoreCase(args[1])
                && "export".equalsIgnoreCase(args[2])) {
            Player target = onlineTarget(viewer, args, 3);
            if (target != null) {
                exportLatestTrace(viewer, target);
            }
            return;
        }
        viewer.sendMessage("Usage: /mmo combat debug [player] | /mmo combat trace export [player]");
    }

    private Player onlineTarget(Player viewer, String[] args, int nameIndex) {
        if (args.length <= nameIndex) {
            return viewer;
        }
        Player target = Bukkit.getPlayerExact(args[nameIndex]);
        if (target == null) {
            viewer.sendMessage(Component.text("Target player is not online.", NamedTextColor.RED));
        }
        return target;
    }

    private void exportLatestTrace(Player viewer, Player target) {
        CombatTrace trace = combatSessions.latestTrace(target).orElse(null);
        if (trace == null) {
            viewer.sendMessage(
                    Component.text(
                            "No completed/cancelled combat trace is available for "
                                    + target.getName()
                                    + ".",
                            NamedTextColor.YELLOW));
            return;
        }
        Result<CombatTrace, CombatSimulationErrorCode> replayed = combatSessions.replayTrace(trace);
        if (replayed instanceof Result.Failure<CombatTrace, CombatSimulationErrorCode> failure) {
            viewer.sendMessage(
                    Component.text(
                            "Trace replay rejected: "
                                    + failure.error().code()
                                    + " "
                                    + failure.detail(),
                            NamedTextColor.RED));
            return;
        }
        try {
            Path exported = traceExporter.export(target.getUniqueId(), trace);
            viewer.sendMessage(
                    Component.text(
                            "Replay verified; canonical trace exported to " + exported,
                            NamedTextColor.GREEN));
        } catch (IOException exception) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.WARNING,
                            "Could not export combat trace for " + target.getUniqueId(),
                            exception);
            viewer.sendMessage(
                    Component.text(
                            "Combat trace export failed; inspect server log.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof DevInventoryHolder holder)
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        String action = action(event.getCurrentItem());
        if (action == null) {
            return;
        }
        switch (action) {
            case "content" -> openContentBrowser(player);
            case "spawner" -> openItemSpawner(player);
            case "back" -> openDevHub(player);
            case "scene" -> {
                player.closeInventory();
                plugin.getServer().getScheduler().runTask(plugin, () -> sceneHub.open(player));
            }
            case "combat" -> {
                player.closeInventory();
                plugin.getServer()
                        .getScheduler()
                        .runTask(plugin, () -> combatSessions.startTrainingMove(player));
            }
            case "progression" -> {
                player.closeInventory();
                simulateProgressionEvidence(player, "meaningful");
            }
            case "teaching" -> {
                player.closeInventory();
                simulateTeaching(player, "success");
                simulateRenown(player, "fresh");
            }
            case "encounter" -> {
                player.closeInventory();
                bossEncounters.handleCommand(
                        player, new String[] {"encounter", "start", UUID.randomUUID().toString()});
            }
            case "locked" ->
                    player.sendActionBar(
                            Component.text(
                                    "Module waits for its owning milestone and test-provenance path.",
                                    NamedTextColor.YELLOW));
            case "close" -> player.closeInventory();
            default -> {
                if (holder.page() == DevInventoryHolder.Page.CONTENT) {
                    player.sendActionBar(Component.text(action, NamedTextColor.GRAY));
                } else if (holder.page() == DevInventoryHolder.Page.ITEM_SPAWNER
                        && action.startsWith("spawn:")) {
                    spawnTestProjection(
                            player,
                            action.substring("spawn:".length()),
                            event.isShiftClick() ? 64 : 1);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DevInventoryHolder) {
            event.setCancelled(true);
        }
    }

    private void showHealth(CommandSender sender) {
        ContentSnapshot snapshot = snapshotSource.get();
        ItemEngine items = itemEngineSource.get();
        MoveEngine moves = moveEngineSource.get();
        AilmentDefinitionEngine ailments = ailmentEngineSource.get();
        sender.sendMessage(
                Component.text("Branz MMO: ", NamedTextColor.GOLD)
                        .append(
                                Component.text(
                                        lifecycle.state().name(),
                                        lifecycle.acceptsSessions()
                                                ? NamedTextColor.GREEN
                                                : NamedTextColor.RED)));
        if (snapshot != null) {
            sender.sendMessage(
                    Component.text(
                            "Content "
                                    + snapshot.manifest().contentVersion()
                                    + " | definitions="
                                    + snapshot.definitions().size()
                                    + " | items="
                                    + (items == null ? 0 : items.all().size())
                                    + " | moves="
                                    + (moves == null ? 0 : moves.all().size())
                                    + " | spells="
                                    + snapshot.definitions()
                                            .byType(
                                                    com.branz.mmorpg.content.schema.DefinitionType
                                                            .SPELL)
                                            .size()
                                    + " | ailments="
                                    + (ailments == null ? 0 : ailments.all().size()),
                            NamedTextColor.GRAY));
        }
        if (sender instanceof Player player) {
            sender.sendMessage(
                    Component.text(
                            "Pack admission: " + packGate.state(player.getUniqueId()).name(),
                            NamedTextColor.GRAY));
            characterSessions
                    .active(player)
                    .ifPresentOrElse(
                            session ->
                                    sender.sendMessage(
                                            Component.text(
                                                    "Character DB session: READY | character="
                                                            + session.characterId().value()
                                                            + " | lease-version="
                                                            + session.lease().version(),
                                                    NamedTextColor.GREEN)),
                            () ->
                                    sender.sendMessage(
                                            Component.text(
                                                    "Character DB session: LOADING",
                                                    NamedTextColor.YELLOW)));
            characterSessions
                    .active(player)
                    .ifPresent(
                            session ->
                                    sender.sendMessage(
                                            Component.text(
                                                    progressionSummary(
                                                            session.snapshot().progressionTracks()),
                                                    NamedTextColor.LIGHT_PURPLE)));
            combatSessions
                    .status(player)
                    .ifPresentOrElse(
                            status ->
                                    sender.sendMessage(
                                            Component.text(
                                                    "Combat session: "
                                                            + status.engagementState()
                                                            + (status.engagementExitTicksRemaining()
                                                                            > 0
                                                                    ? " (exit="
                                                                            + status
                                                                                    .engagementExitTicksRemaining()
                                                                            + "t)"
                                                                    : "")
                                                            + " | weapon="
                                                            + status.weaponState()
                                                            + " | action="
                                                            + status.actionPhase()
                                                                    .map(Enum::name)
                                                                    .orElse("IDLE")
                                                            + " | bow="
                                                            + status.bowDrawPhase()
                                                                    .map(Enum::name)
                                                                    .orElseGet(
                                                                            () ->
                                                                                    status
                                                                                                            .bowRecoveryTicksRemaining()
                                                                                                    > 0
                                                                                            ? "RECOVERY("
                                                                                                    + status
                                                                                                            .bowRecoveryTicksRemaining()
                                                                                                    + "t)"
                                                                                            : "IDLE")
                                                            + " | crossbow="
                                                            + status.crossbowPhase()
                                                                    .map(Enum::name)
                                                                    .orElse("IDLE")
                                                            + (status
                                                                                    .crossbowRecoveryTicksRemaining()
                                                                            > 0
                                                                    ? "(recovery="
                                                                            + status
                                                                                    .crossbowRecoveryTicksRemaining()
                                                                            + "t)"
                                                                    : "")
                                                            + (status
                                                                            .crossbowCheckpointCommitPending()
                                                                    ? "(COMMITTING)"
                                                                    : "")
                                                            + " | spell="
                                                            + status.spellCastPhase()
                                                                    .map(Enum::name)
                                                                    .orElse("IDLE")
                                                            + (status.spellCommitPending()
                                                                    ? "(COMMITTING)"
                                                                    : "")
                                                            + " selected="
                                                            + status.selectedSpell()
                                                                    .map(DefinitionId::value)
                                                                    .orElse("none")
                                                            + " zones="
                                                            + status.activeZones()
                                                            + " imbue="
                                                            + status.imbuementCharges()
                                                            + " | projectiles="
                                                            + status.activeProjectiles()
                                                            + " | ammo="
                                                            + status.selectedAmmo()
                                                                    .map(ammo -> ammo.value() + "=")
                                                                    .orElse("none=")
                                                            + status.selectedAmmoQuantity()
                                                            + (status.bowAmmoCommitPending()
                                                                    ? "(COMMITTING)"
                                                                    : "")
                                                            + " | quiver="
                                                            + status.quiverUsedCapacity()
                                                            + "/"
                                                            + status.quiverCapacity()
                                                            + (status
                                                                                    .ammoSwitchHandlingTicksRemaining()
                                                                            > 0
                                                                    ? "(handling="
                                                                            + status
                                                                                    .ammoSwitchHandlingTicksRemaining()
                                                                            + "t)"
                                                                    : "")
                                                            + " | dodge="
                                                            + status.dodgeLoad()
                                                            + "/"
                                                            + status.dodgePhase()
                                                                    .map(Enum::name)
                                                                    .orElse("IDLE")
                                                            + " | guard="
                                                            + status.guardPhase()
                                                            + "("
                                                            + Math.round(
                                                                            status.guardStability()
                                                                                    * 10.0)
                                                                    / 10.0
                                                            + ")"
                                                            + " | cc="
                                                            + status.crowdControl()
                                                                    .map(
                                                                            severity ->
                                                                                    severity
                                                                                            + "("
                                                                                            + status
                                                                                                    .crowdControlTicksRemaining()
                                                                                            + "t)")
                                                                    .orElse("NONE")
                                                            + " | health="
                                                            + Math.round(status.health() * 10.0)
                                                                    / 10.0
                                                            + "/"
                                                            + Math.round(
                                                                            status.maximumHealth()
                                                                                    * 10.0)
                                                                    / 10.0
                                                            + (status.dead() ? "(DEAD)" : "")
                                                            + " | stamina="
                                                            + status.stamina()
                                                            + " (reserved="
                                                            + status.reservedStamina()
                                                            + ")"
                                                            + " | mana="
                                                            + status.mana()
                                                            + " (reserved="
                                                            + status.reservedMana()
                                                            + ")"
                                                            + status.lastResolution()
                                                                    .map(
                                                                            resolution ->
                                                                                    " | last="
                                                                                            + resolution)
                                                                    .orElse(""),
                                                    NamedTextColor.AQUA)),
                            () ->
                                    sender.sendMessage(
                                            Component.text(
                                                    "Combat session: LOADING",
                                                    NamedTextColor.YELLOW)));
        }
    }

    private void simulateProgressionEvidence(Player player, String scenario) {
        if (!characterSessions.ready(player)) {
            player.sendMessage(
                    Component.text("Character session is not ready.", NamedTextColor.RED));
            return;
        }
        ProgressionScenario selected = ProgressionScenario.resolve(scenario);
        if (selected == null) {
            progressionUsage(player);
            return;
        }
        ContentSnapshot snapshot = snapshotSource.get();
        if (snapshot == null) {
            player.sendMessage(
                    Component.text("Content snapshot is not ready.", NamedTextColor.RED));
            return;
        }
        EvidenceCandidate candidate =
                progressionCandidate(player, scenario, selected, snapshot, UUID.randomUUID());
        EvidenceDecision decision = progressionEvidence.evaluate(candidate, selected.context());
        NamedTextColor color = decision.accepted() ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        player.sendMessage(
                Component.text(
                        "Progression Evidence Lab ["
                                + scenario
                                + "]: "
                                + (decision.accepted() ? "ACCEPTED" : "SUPPRESSED")
                                + " | award="
                                + String.format(Locale.ROOT, "%.5f", decision.awardedEvidence())
                                + " | band="
                                + decision.previousBand()
                                + "->"
                                + decision.resultingBand()
                                + " | reason="
                                + decision.suppressionReason(),
                        color));
        player.sendMessage(
                Component.text(
                        "Simulation only: no character progression was persisted.",
                        NamedTextColor.GRAY));
    }

    private void simulateTeaching(Player player, String scenario) {
        CharacterId teacherId = new CharacterId(UUID.randomUUID());
        CharacterId studentId = new CharacterId(player.getUniqueId());
        KnowledgeKey technique =
                new KnowledgeKey(
                        KnowledgeType.TECHNIQUE, DefinitionId.of("technique.greatsword.cleave"));
        KnowledgeKey foundation =
                new KnowledgeKey(
                        KnowledgeType.FOUNDATION, DefinitionId.of("foundation.greatsword"));
        KnowledgeProfile teacherProfile =
                new KnowledgeProfile(Set.of(technique), Map.of(), Set.of());
        KnowledgeProfile studentProfile = new KnowledgeProfile(Set.of(), Map.of(), Set.of());
        LearningRequirements requirements = LearningRequirements.none();
        boolean teacherReady = true;
        if ("missing-teacher".equals(scenario)) {
            teacherProfile = studentProfile;
        } else if ("unready-teacher".equals(scenario)) {
            teacherReady = false;
        } else if ("student-prerequisite".equals(scenario)) {
            requirements = new LearningRequirements(Set.of(foundation), Map.of(), Set.of());
        } else if (!Set.of("success", "duplicate-action", "expired", "disconnect")
                .contains(scenario)) {
            teachingUsage(player);
            return;
        }

        Result<TeachingSession, TeachingErrorCode> started =
                teachingEngine.start(
                        UUID.randomUUID(),
                        teacherId,
                        studentId,
                        technique,
                        requirements,
                        teacherProfile,
                        teacherReady,
                        true,
                        true,
                        studentProfile,
                        100);
        if (started instanceof Result.Failure<TeachingSession, TeachingErrorCode> failure) {
            teachingLabMessage(player, scenario, failure.error().code(), failure.detail(), false);
            return;
        }
        TeachingSession session =
                ((Result.Success<TeachingSession, TeachingErrorCode>) started).value();
        session =
                teachingSuccess(
                        player,
                        scenario,
                        teachingEngine.demonstrate(
                                session,
                                teacherId,
                                DefinitionId.of("move.training_greatsword.committed_cleave"),
                                101));
        if (session == null) {
            return;
        }
        if ("expired".equals(scenario)) {
            session = teachingEngine.expire(session, session.expiresTick());
        } else if ("disconnect".equals(scenario)) {
            session = teachingEngine.cancelForDisconnect(session, studentId, 102);
        } else {
            UUID actionId = UUID.randomUUID();
            int uniqueActions = "duplicate-action".equals(scenario) ? 1 : 3;
            for (int index = 0; index < 3; index++) {
                UUID observedActionId = uniqueActions == 1 ? actionId : UUID.randomUUID();
                session =
                        teachingSuccess(
                                player,
                                scenario,
                                teachingEngine.observeStudentAction(
                                        session,
                                        studentId,
                                        observedActionId,
                                        DefinitionId.of(
                                                "move.training_greatsword.committed_cleave"),
                                        true,
                                        102 + index));
                if (session == null) {
                    return;
                }
            }
        }
        Result<TeachingCompletion, TeachingErrorCode> completion =
                teachingEngine.completion(session, Math.min(110, session.expiresTick()));
        if (completion instanceof Result.Success<TeachingCompletion, TeachingErrorCode> success) {
            teachingLabMessage(
                    player,
                    scenario,
                    "READY_TO_COMMIT",
                    "session=" + success.value().teachingSessionId(),
                    true);
            return;
        }
        Result.Failure<TeachingCompletion, TeachingErrorCode> failure =
                (Result.Failure<TeachingCompletion, TeachingErrorCode>) completion;
        teachingLabMessage(player, scenario, failure.error().code(), failure.detail(), false);
    }

    private void recordTeaching(
            Player teacher, Player student, UUID teachingSessionId, UUID deedId) {
        if (teacher.getUniqueId().equals(student.getUniqueId())) {
            teacher.sendMessage(
                    Component.text(
                            "Teacher and student must be different players.", NamedTextColor.RED));
            return;
        }
        ContentSnapshot snapshot = snapshotSource.get();
        if (snapshot == null) {
            teacher.sendMessage(
                    Component.text("Content snapshot is not ready.", NamedTextColor.RED));
            return;
        }
        KnowledgeKey technique =
                new KnowledgeKey(
                        KnowledgeType.TECHNIQUE, DefinitionId.of("technique.greatsword.cleave"));
        TeachingCommitRequest request =
                new TeachingCommitRequest(
                        new TeachingCompletion(
                                teachingSessionId,
                                new CharacterId(teacher.getUniqueId()),
                                new CharacterId(student.getUniqueId()),
                                technique),
                        new RenownDeedCandidate(
                                deedId,
                                new CharacterId(teacher.getUniqueId()),
                                DefinitionId.of("renown.mentorship"),
                                "mentorship:" + technique.id().value(),
                                20,
                                snapshot.manifest().contentVersion()));
        teacher.sendMessage(
                Component.text(
                        "Committing teaching fixture through PostgreSQL…", NamedTextColor.YELLOW));
        characterSessions.commitTeaching(
                teacher,
                student,
                request,
                result -> {
                    if (result
                            instanceof
                            Result.Failure<TeachingSessionCommitResult, CharacterSessionErrorCode>
                                    failure) {
                        teacher.sendMessage(
                                Component.text(
                                        "Teaching record failed: "
                                                + failure.error().code()
                                                + " "
                                                + failure.detail(),
                                        NamedTextColor.RED));
                        return;
                    }
                    TeachingSessionCommitResult committed =
                            ((Result.Success<
                                                    TeachingSessionCommitResult,
                                                    CharacterSessionErrorCode>)
                                            result)
                                    .value();
                    long renown =
                            committed
                                    .teacherSession()
                                    .snapshot()
                                    .renown()
                                    .map(record -> record.renown())
                                    .orElse(0L);
                    String replay = committed.execution().replayed() ? " REPLAY" : "";
                    teacher.sendMessage(
                            Component.text(
                                    "Teaching PERSISTED"
                                            + replay
                                            + " | student="
                                            + student.getName()
                                            + " | learned="
                                            + technique.id().value()
                                            + " | award="
                                            + committed
                                                    .execution()
                                                    .teacherDeed()
                                                    .decision()
                                                    .awardedRenown()
                                            + " | Renown="
                                            + renown
                                            + " | session-id="
                                            + teachingSessionId
                                            + " | deed-id="
                                            + deedId,
                                    NamedTextColor.GREEN));
                    student.sendMessage(
                            Component.text(
                                    "Learned "
                                            + technique.id().value()
                                            + " from "
                                            + teacher.getName()
                                            + ".",
                                    NamedTextColor.LIGHT_PURPLE));
                });
    }

    private void showKnowledgeStatus(Player viewer, Player target) {
        characterSessions
                .active(target)
                .ifPresentOrElse(
                        session -> {
                            String knowledge =
                                    session.snapshot().learnedKnowledge().isEmpty()
                                            ? "none"
                                            : session.snapshot().learnedKnowledge().stream()
                                                    .map(record -> record.knowledge().id().value())
                                                    .collect(
                                                            java.util.stream.Collectors.joining(
                                                                    ", "));
                            long renown =
                                    session.snapshot()
                                            .renown()
                                            .map(record -> record.renown())
                                            .orElse(0L);
                            viewer.sendMessage(
                                    Component.text(
                                            "Knowledge/Renown ["
                                                    + target.getName()
                                                    + "]: learned="
                                                    + knowledge
                                                    + " | Renown="
                                                    + renown,
                                            NamedTextColor.LIGHT_PURPLE));
                        },
                        () ->
                                viewer.sendMessage(
                                        Component.text(
                                                "Target character session is not ready.",
                                                NamedTextColor.RED)));
    }

    private static UUID parseUuid(Player player, String[] args, int index, String fieldName) {
        if (args.length <= index) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(args[index]);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text(fieldName + " UUID is invalid.", NamedTextColor.RED));
            return null;
        }
    }

    private TeachingSession teachingSuccess(
            Player player, String scenario, Result<TeachingSession, TeachingErrorCode> result) {
        if (result instanceof Result.Success<TeachingSession, TeachingErrorCode> success) {
            return success.value();
        }
        Result.Failure<TeachingSession, TeachingErrorCode> failure =
                (Result.Failure<TeachingSession, TeachingErrorCode>) result;
        teachingLabMessage(player, scenario, failure.error().code(), failure.detail(), false);
        return null;
    }

    private static void teachingLabMessage(
            Player player, String scenario, String result, String detail, boolean accepted) {
        player.sendMessage(
                Component.text(
                        "Teaching Lab [" + scenario + "]: " + result + " | " + detail,
                        accepted ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        player.sendMessage(
                Component.text(
                        "Simulation only: no knowledge or teacher reward was persisted.",
                        NamedTextColor.GRAY));
    }

    private void simulateRenown(Player player, String scenario) {
        int repetitions;
        boolean duplicate;
        switch (scenario) {
            case "fresh" -> {
                repetitions = 0;
                duplicate = false;
            }
            case "repeat-1" -> {
                repetitions = 1;
                duplicate = false;
            }
            case "repeat-2" -> {
                repetitions = 2;
                duplicate = false;
            }
            case "exhausted" -> {
                repetitions = 3;
                duplicate = false;
            }
            case "duplicate" -> {
                repetitions = 0;
                duplicate = true;
            }
            default -> {
                renownUsage(player);
                return;
            }
        }
        RenownDeedCandidate candidate =
                new RenownDeedCandidate(
                        UUID.randomUUID(),
                        new CharacterId(player.getUniqueId()),
                        DefinitionId.of("renown.mentorship"),
                        "dev-lab:teacher:student:technique:utc-day",
                        20,
                        "dev-lab-v1");
        RenownDecision decision =
                renownEngine.evaluate(candidate, new RenownContext(75, repetitions, duplicate));
        player.sendMessage(
                Component.text(
                        "Renown Lab ["
                                + scenario
                                + "]: "
                                + (decision.accepted() ? "ACCEPTED" : "SUPPRESSED")
                                + " | award="
                                + decision.awardedRenown()
                                + " | total="
                                + decision.resultingRenown()
                                + " | factor="
                                + decision.repetitionFactor()
                                + " | reason="
                                + decision.suppressionReason(),
                        decision.accepted() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        player.sendMessage(
                Component.text("Simulation only: no Renown was persisted.", NamedTextColor.GRAY));
    }

    private static void teachingUsage(Player player) {
        player.sendMessage(
                "Usage: /mmo teaching start <student> <technique-id> | /mmo teaching session | "
                        + "/mmo teaching cancel | /mmo teaching status [player] | /mmo teaching simulate "
                        + "<success|missing-teacher|unready-teacher|student-prerequisite|duplicate-action|expired|disconnect> "
                        + "| /mmo teaching record <student> [teaching-session-uuid] [deed-uuid]");
    }

    private static void renownUsage(Player player) {
        player.sendMessage(
                "Usage: /mmo renown simulate <fresh|repeat-1|repeat-2|exhausted|duplicate>");
    }

    private static void knowledgeUsage(Player player) {
        player.sendMessage(
                "Usage: /mmo knowledge acquire <FORM|SPELL> <definition-id> [acquisition-uuid]");
    }

    private static void consumableUsage(Player player) {
        player.sendMessage(
                "Usage: /mmo consumable status | /mmo consumable persist [operation-uuid] | "
                        + "/mmo consumable checkpoint <capture|restore> <checkpoint-uuid> "
                        + "[operation-uuid] | /mmo consumable simulate "
                        + "<flask|timeline|ailment|category>");
    }

    private void recordProgressionEvidence(Player player, String scenario, UUID evidenceId) {
        if (!characterSessions.ready(player)) {
            player.sendMessage(
                    Component.text("Character session is not ready.", NamedTextColor.RED));
            return;
        }
        ProgressionScenario selected = ProgressionScenario.resolve(scenario);
        ContentSnapshot snapshot = snapshotSource.get();
        if (selected == null || snapshot == null) {
            progressionUsage(player);
            return;
        }
        EvidenceCandidate candidate =
                progressionCandidate(player, scenario, selected, snapshot, evidenceId);
        player.sendMessage(
                Component.text("Recording progression evidence…", NamedTextColor.YELLOW));
        characterSessions.recordProgressionEvidence(
                player,
                List.of(candidate),
                result -> {
                    if (result
                            instanceof
                            Result.Failure<
                                            ProgressionEvidenceCommitResult,
                                            CharacterSessionErrorCode>
                                    failure) {
                        player.sendMessage(
                                Component.text(
                                        "Progression record failed: "
                                                + failure.error().code()
                                                + " "
                                                + failure.detail(),
                                        NamedTextColor.RED));
                        return;
                    }
                    ProgressionEvidenceExecution execution =
                            ((Result.Success<
                                                    ProgressionEvidenceCommitResult,
                                                    CharacterSessionErrorCode>)
                                            result)
                                    .value()
                                    .executions()
                                    .getFirst();
                    EvidenceDecision decision = execution.evidence().decision();
                    player.sendMessage(
                            Component.text(
                                    "Progression Evidence Lab ["
                                            + scenario
                                            + "]: PERSISTED"
                                            + (execution.replayed() ? " REPLAY" : "")
                                            + " | award="
                                            + String.format(
                                                    Locale.ROOT, "%.5f", decision.awardedEvidence())
                                            + " | band="
                                            + decision.previousBand()
                                            + "->"
                                            + decision.resultingBand()
                                            + " | reason="
                                            + decision.suppressionReason()
                                            + " | evidence-id="
                                            + evidenceId,
                                    decision.accepted()
                                            ? NamedTextColor.GREEN
                                            : NamedTextColor.YELLOW));
                });
    }

    private static EvidenceCandidate progressionCandidate(
            Player player,
            String scenario,
            ProgressionScenario selected,
            ContentSnapshot snapshot,
            UUID evidenceId) {
        return new EvidenceCandidate(
                evidenceId,
                new CharacterId(player.getUniqueId()),
                new EncounterId(evidenceId),
                ProgressionTrack.mastery("greatsword"),
                "dev-lab-" + scenario,
                snapshot.manifest().contentVersion(),
                selected.targetKind(),
                selected.outcome(),
                10.0,
                selected.challengeRating(),
                100.0,
                0.8,
                0.8,
                0.8);
    }

    private void showProgressionStatus(Player player) {
        characterSessions
                .active(player)
                .ifPresentOrElse(
                        session ->
                                player.sendMessage(
                                        Component.text(
                                                progressionSummary(
                                                        session.snapshot().progressionTracks()),
                                                NamedTextColor.LIGHT_PURPLE)),
                        () ->
                                player.sendMessage(
                                        Component.text(
                                                "Character session is not ready.",
                                                NamedTextColor.RED)));
    }

    private static String progressionSummary(List<ProgressionTrackRecord> tracks) {
        if (tracks.isEmpty()) {
            return "Progression readiness: no meaningful evidence yet";
        }
        return "Progression readiness: "
                + tracks.stream()
                        .map(
                                track ->
                                        track.track().id().value()
                                                + "="
                                                + ReadinessBand.fromEvidence(track.evidence()))
                        .collect(java.util.stream.Collectors.joining(", "));
    }

    private static void progressionUsage(Player player) {
        player.sendMessage(
                "Usage: /mmo progression <status|simulate|record> "
                        + "<meaningful|dummy-intro|dummy-capped|invulnerable|loop|zero-risk|low-challenge|repeated> "
                        + "[evidence-uuid]");
    }

    private void openDevHub(Player player) {
        if (!devToolsAllowed(player)) {
            player.sendMessage(
                    Component.text(
                            "MMO dev tools are disabled for this environment/account.",
                            NamedTextColor.RED));
            return;
        }
        DevInventoryHolder holder = new DevInventoryHolder(DevInventoryHolder.Page.HUB);
        Inventory inventory =
                Bukkit.createInventory(
                        holder, 54, Component.text("MMO Development Console", NamedTextColor.AQUA));
        holder.attach(inventory);
        for (DevModule module : MODULES) {
            inventory.setItem(
                    module.slot(), button(module.material(), module.label(), module.action()));
        }
        player.openInventory(inventory);
    }

    private void openContentBrowser(Player player) {
        DevInventoryHolder holder = new DevInventoryHolder(DevInventoryHolder.Page.CONTENT);
        Inventory inventory =
                Bukkit.createInventory(
                        holder, 54, Component.text("Content Browser — Items", NamedTextColor.AQUA));
        holder.attach(inventory);
        ItemEngine engine = itemEngineSource.get();
        if (engine != null) {
            int slot = 0;
            for (ItemDefinition definition : engine.all()) {
                if (slot >= 45) {
                    break;
                }
                ItemStack item =
                        button(
                                Material.PAPER,
                                definition.id().value(),
                                "inspect:" + definition.id().value());
                item.editMeta(
                        meta ->
                                meta.lore(
                                        List.of(
                                                Component.text(
                                                        "class=" + definition.itemClass(),
                                                        NamedTextColor.GRAY),
                                                Component.text(
                                                        "asset=" + definition.assetId(),
                                                        NamedTextColor.DARK_GRAY))));
                inventory.setItem(slot++, item);
            }
        }
        inventory.setItem(49, button(Material.ARROW, "Back", "back"));
        player.openInventory(inventory);
    }

    private void openItemSpawner(Player player) {
        DevInventoryHolder holder = new DevInventoryHolder(DevInventoryHolder.Page.ITEM_SPAWNER);
        Inventory inventory =
                Bukkit.createInventory(
                        holder, 54, Component.text("Persisted Test Grants", NamedTextColor.AQUA));
        holder.attach(inventory);
        ItemEngine engine = itemEngineSource.get();
        if (engine != null) {
            int slot = 0;
            for (ItemDefinition definition : engine.all()) {
                if (slot >= 45) {
                    break;
                }
                inventory.setItem(
                        slot++,
                        button(
                                Material.BARRIER,
                                definition.id().value() + " (Shift = lot x64)",
                                "spawn:" + definition.id().value()));
            }
        }
        inventory.setItem(49, button(Material.ARROW, "Back", "back"));
        player.openInventory(inventory);
    }

    private void spawnTestProjection(Player player, String definitionId, int requestedQuantity) {
        ItemEngine engine = itemEngineSource.get();
        ContentSnapshot snapshot = snapshotSource.get();
        if (engine == null || snapshot == null) {
            player.sendActionBar(Component.text("Item Engine is not ready.", NamedTextColor.RED));
            return;
        }
        ItemDefinition definition = engine.find(DefinitionId.of(definitionId)).orElse(null);
        if (definition == null) {
            player.sendActionBar(
                    Component.text("Definition is no longer active.", NamedTextColor.RED));
            return;
        }
        int quantity = definition.itemClass() == ItemClass.STACKABLE_LOT ? requestedQuantity : 1;
        player.sendActionBar(
                Component.text("Committing test grant through PostgreSQL…", NamedTextColor.YELLOW));
        characterSessions.grantTestValue(
                player,
                definition,
                quantity,
                snapshot.manifest().contentVersion(),
                result -> {
                    if (result.isSuccess()) {
                        player.sendActionBar(
                                Component.text(
                                        "Persisted test value x"
                                                + quantity
                                                + " granted and projected.",
                                        NamedTextColor.GREEN));
                        return;
                    }
                    Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode> failure =
                            (Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>)
                                    result;
                    player.sendMessage(
                            Component.text(
                                    "Test grant failed: "
                                            + failure.error().code()
                                            + " "
                                            + failure.detail(),
                                    NamedTextColor.RED));
                });
    }

    private ItemStack button(Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        item.editMeta(
                meta -> {
                    meta.displayName(Component.text(name, NamedTextColor.YELLOW));
                    meta.getPersistentDataContainer()
                            .set(actionKey, PersistentDataType.STRING, action);
                });
        return item;
    }

    private String action(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta()
                .getPersistentDataContainer()
                .get(actionKey, PersistentDataType.STRING);
    }

    private boolean devToolsAllowed(Player player) {
        String environment =
                plugin.getConfig()
                        .getString("environment", "LOCAL")
                        .trim()
                        .toUpperCase(Locale.ROOT);
        boolean environmentAllowed =
                environment.equals("LOCAL")
                        || environment.equals("CONTENT_DEV")
                        || environment.equals("INTEGRATION")
                        || environment.equals("STAGING");
        return environmentAllowed
                && plugin.getConfig().getBoolean("dev-tools.enabled", false)
                && player.hasPermission("branzmmo.dev");
    }

    private record ProgressionScenario(
            EvidenceTargetKind targetKind,
            EncounterOutcome outcome,
            double challengeRating,
            EvidenceContext context) {
        private static ProgressionScenario resolve(String scenario) {
            return switch (scenario) {
                case "meaningful" ->
                        scenario(
                                EvidenceTargetKind.MEANINGFUL_ENCOUNTER,
                                100.0,
                                new EvidenceContext(90.0, 0, 0.0, false));
                case "dummy-intro" ->
                        scenario(
                                EvidenceTargetKind.TRAINING_DUMMY,
                                100.0,
                                new EvidenceContext(24.0, 0, 0.0, false));
                case "dummy-capped" ->
                        scenario(
                                EvidenceTargetKind.TRAINING_DUMMY,
                                100.0,
                                new EvidenceContext(25.0, 0, 0.0, false));
                case "invulnerable" ->
                        scenario(
                                EvidenceTargetKind.INVULNERABLE_TARGET,
                                100.0,
                                new EvidenceContext(0.0, 0, 0.0, false));
                case "loop" ->
                        scenario(
                                EvidenceTargetKind.SELF_CREATED_LOOP,
                                100.0,
                                new EvidenceContext(0.0, 0, 0.0, false));
                case "zero-risk" ->
                        scenario(
                                EvidenceTargetKind.ZERO_RISK_INTERACTION,
                                100.0,
                                new EvidenceContext(0.0, 0, 0.0, false));
                case "low-challenge" ->
                        scenario(
                                EvidenceTargetKind.MEANINGFUL_ENCOUNTER,
                                29.0,
                                new EvidenceContext(0.0, 0, 0.0, false));
                case "repeated" ->
                        scenario(
                                EvidenceTargetKind.MEANINGFUL_ENCOUNTER,
                                100.0,
                                new EvidenceContext(400.0, 8, 300.0, false));
                default -> null;
            };
        }

        private static ProgressionScenario scenario(
                EvidenceTargetKind targetKind, double challengeRating, EvidenceContext context) {
            return new ProgressionScenario(
                    targetKind, EncounterOutcome.VICTORY, challengeRating, context);
        }
    }

    private record DevModule(int slot, Material material, String label, String action) {}
}
