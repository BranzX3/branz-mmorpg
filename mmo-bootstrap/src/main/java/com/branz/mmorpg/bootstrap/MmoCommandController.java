package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.move.MoveEngine;
import com.branz.mmorpg.combat.trace.CombatSimulationErrorCode;
import com.branz.mmorpg.combat.trace.CombatTrace;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.progression.evidence.EncounterOutcome;
import com.branz.mmorpg.progression.evidence.EvidenceCandidate;
import com.branz.mmorpg.progression.evidence.EvidenceContext;
import com.branz.mmorpg.progression.evidence.EvidenceDecision;
import com.branz.mmorpg.progression.evidence.EvidenceTargetKind;
import com.branz.mmorpg.progression.evidence.ProgressionEvidenceEngine;
import com.branz.mmorpg.progression.evidence.ProgressionTrack;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
                    new DevModule(28, Material.ZOMBIE_HEAD, "Encounter Spawner (locked)", "locked"),
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
    private final SceneHubController sceneHub;
    private final CharacterSessionController characterSessions;
    private final CombatSessionController combatSessions;
    private final CombatTraceFileExporter traceExporter;
    private final ProgressionEvidenceEngine progressionEvidence = new ProgressionEvidenceEngine();
    private final NamespacedKey actionKey;

    MmoCommandController(
            JavaPlugin plugin,
            BootstrapLifecycle lifecycle,
            ResourcePackGate packGate,
            Supplier<ContentSnapshot> snapshotSource,
            Supplier<ItemEngine> itemEngineSource,
            Supplier<MoveEngine> moveEngineSource,
            SceneHubController sceneHub,
            CharacterSessionController characterSessions,
            CombatSessionController combatSessions) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.packGate = Objects.requireNonNull(packGate, "packGate");
        this.snapshotSource = Objects.requireNonNull(snapshotSource, "snapshotSource");
        this.itemEngineSource = Objects.requireNonNull(itemEngineSource, "itemEngineSource");
        this.moveEngineSource = Objects.requireNonNull(moveEngineSource, "moveEngineSource");
        this.sceneHub = Objects.requireNonNull(sceneHub, "sceneHub");
        this.characterSessions = Objects.requireNonNull(characterSessions, "characterSessions");
        this.combatSessions = Objects.requireNonNull(combatSessions, "combatSessions");
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
        sender.sendMessage(
                "Usage: /mmo <health|dev|combat debug|combat trace export|progression simulate>");
        return true;
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
        if (args.length < 3 || !"simulate".equalsIgnoreCase(args[1])) {
            progressionUsage(player);
            return;
        }
        simulateProgressionEvidence(player, args[2].toLowerCase(Locale.ROOT));
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
                                            .size(),
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
                new EvidenceCandidate(
                        UUID.randomUUID(),
                        new CharacterId(player.getUniqueId()),
                        new EncounterId(UUID.randomUUID()),
                        ProgressionTrack.mastery("greatsword"),
                        "dev-lab-move-set-a",
                        snapshot.manifest().contentVersion(),
                        selected.targetKind(),
                        selected.outcome(),
                        10.0,
                        selected.challengeRating(),
                        100.0,
                        0.8,
                        0.8,
                        0.8);
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

    private static void progressionUsage(Player player) {
        player.sendMessage(
                "Usage: /mmo progression simulate "
                        + "<meaningful|dummy-intro|dummy-capped|invulnerable|loop|zero-risk|low-challenge|repeated>");
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
