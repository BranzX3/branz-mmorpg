package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.lifeskills.node.ResourceNodePhase;
import com.branz.mmorpg.lifeskills.node.ResourceNodeRuntime;
import com.branz.mmorpg.persistence.transaction.CharacterLifeskillStateRecord;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.ResourceNodeStateRecord;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Main-thread Paper bridge for the durable resource-node acceptance lab. */
final class ResourceNodeController implements Listener {
    private final JavaPlugin plugin;
    private final CharacterSessionController characterSessions;
    private final ItemEngine items;
    private final DurableResourceNodeService service;
    private final String contentVersion;
    private final Clock clock;
    private final Map<UUID, LiveResourceNodeReservation> activeJobs = new ConcurrentHashMap<>();
    private int reconciliationTaskId = -1;

    ResourceNodeController(
            JavaPlugin plugin,
            CharacterSessionController characterSessions,
            ItemEngine items,
            DurableResourceNodeService service,
            String contentVersion,
            Clock clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characterSessions = Objects.requireNonNull(characterSessions, "characterSessions");
        this.items = Objects.requireNonNull(items, "items");
        this.service = Objects.requireNonNull(service, "service");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    void start() {
        reconcile(true);
        reconciliationTaskId =
                plugin.getServer()
                        .getScheduler()
                        .scheduleSyncRepeatingTask(plugin, () -> reconcile(false), 100L, 100L);
    }

    void shutdown() {
        if (reconciliationTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(reconciliationTaskId);
            reconciliationTaskId = -1;
        }
        activeJobs.clear();
    }

    void handleCommand(Player player, String[] args) {
        if (args.length == 2 && "tool".equalsIgnoreCase(args[1])) {
            grantTool(player);
            return;
        }
        if (args.length >= 2 && "harvest".equalsIgnoreCase(args[1])) {
            int focusCost = parseFocus(player, args);
            if (focusCost >= 0) {
                reserve(player, focusCost);
            }
            return;
        }
        if (args.length == 2 && "status".equalsIgnoreCase(args[1])) {
            status(player);
            return;
        }
        player.sendMessage("Usage: /mmo node <tool|harvest [focus 0-5]|status>");
    }

    private void grantTool(Player player) {
        Optional<LoadedCharacterSession> session = characterSessions.active(player);
        if (session.isEmpty() || !characterSessions.ready(player)) {
            player.sendMessage(
                    Component.text("Character session is not ready.", NamedTextColor.RED));
            return;
        }
        if (findTool(session.orElseThrow()).isPresent()) {
            player.sendMessage(
                    Component.text(
                            "The durable Node Lab pickaxe is already owned.",
                            NamedTextColor.YELLOW));
            return;
        }
        ItemDefinition tool = items.find(service.content().toolDefinitionId()).orElse(null);
        if (tool == null) {
            player.sendMessage(
                    Component.text("Authored Node Lab tool is unavailable.", NamedTextColor.RED));
            return;
        }
        characterSessions.grantTestValue(
                player,
                tool,
                contentVersion,
                result -> {
                    if (result instanceof Result.Failure<LoadedCharacterSession, ?> failure) {
                        player.sendMessage(
                                Component.text(
                                        "Pickaxe grant failed: " + failure.detail(),
                                        NamedTextColor.RED));
                    } else {
                        player.sendMessage(
                                Component.text(
                                        "Durable Node Lab pickaxe committed to PostgreSQL.",
                                        NamedTextColor.GREEN));
                    }
                });
    }

    private void reserve(Player player, int focusCost) {
        if (activeJobs.containsKey(player.getUniqueId())) {
            player.sendMessage(
                    Component.text("A harvest is already in progress.", NamedTextColor.YELLOW));
            return;
        }
        LoadedCharacterSession session =
                characterSessions.beginExternalValueMutation(player).orElse(null);
        if (session == null) {
            player.sendMessage(
                    Component.text(
                            "Character value state is busy or not ready.", NamedTextColor.RED));
            return;
        }
        ItemLocationRecord tool = findTool(session).orElse(null);
        if (tool == null) {
            finishFailure(player, session, "Use /mmo node tool before harvesting the Paper node.");
            return;
        }
        UUID reserveOperationId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID commitOperationId = UUID.randomUUID();
        long tick = plugin.getServer().getCurrentTick();
        player.sendActionBar(
                Component.text("Reserving node and exact pickaxe...", NamedTextColor.YELLOW));
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<LiveResourceNodeReservation, LiveResourceNodeErrorCode>
                                    reserved =
                                            service.reserve(
                                                    session,
                                                    tool,
                                                    focusCost,
                                                    tick,
                                                    clock.instant(),
                                                    reserveOperationId,
                                                    reservationId,
                                                    commitOperationId);
                            if (reserved
                                    instanceof
                                    Result.Failure<
                                                    LiveResourceNodeReservation,
                                                    LiveResourceNodeErrorCode>
                                            failure) {
                                completeFailure(player, session, failure.detail());
                                return;
                            }
                            LiveResourceNodeReservation job =
                                    ((Result.Success<
                                                            LiveResourceNodeReservation,
                                                            LiveResourceNodeErrorCode>)
                                                    reserved)
                                            .value();
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> reloaded =
                                    characterSessions.reloadExternalValueMutation(session);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    characterSessions.completeExternalValueMutation(
                                                            session,
                                                            reloaded,
                                                            completed -> {
                                                                if (!completed.isSuccess()) {
                                                                    player.sendMessage(
                                                                            Component.text(
                                                                                    "Node reserved, but projection refresh failed.",
                                                                                    NamedTextColor
                                                                                            .RED));
                                                                    return;
                                                                }
                                                                activeJobs.put(
                                                                        player.getUniqueId(), job);
                                                                long delay =
                                                                        Math.max(
                                                                                1,
                                                                                job.commitAtTick()
                                                                                        - plugin.getServer()
                                                                                                .getCurrentTick());
                                                                player.sendActionBar(
                                                                        Component.text(
                                                                                "Mining... commit in "
                                                                                        + delay
                                                                                        + " ticks",
                                                                                NamedTextColor
                                                                                        .AQUA));
                                                                plugin.getServer()
                                                                        .getScheduler()
                                                                        .runTaskLater(
                                                                                plugin,
                                                                                () ->
                                                                                        commit(
                                                                                                player,
                                                                                                job),
                                                                                delay);
                                                            }));
                        });
    }

    private void commit(Player player, LiveResourceNodeReservation job) {
        if (!player.isOnline() || activeJobs.get(player.getUniqueId()) != job) {
            return;
        }
        LoadedCharacterSession session =
                characterSessions.beginExternalValueMutation(player).orElse(null);
        if (session == null) {
            activeJobs.remove(player.getUniqueId(), job);
            player.sendMessage(
                    Component.text(
                            "Harvest commit could not acquire the character value lock; reservation will recover by wall clock.",
                            NamedTextColor.RED));
            return;
        }
        long tick = plugin.getServer().getCurrentTick();
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<LiveResourceNodeHarvest, LiveResourceNodeErrorCode> harvested =
                                    service.harvest(session, job, tick, clock.instant());
                            if (harvested
                                    instanceof
                                    Result.Failure<
                                                    LiveResourceNodeHarvest,
                                                    LiveResourceNodeErrorCode>
                                            failure) {
                                activeJobs.remove(player.getUniqueId(), job);
                                completeFailure(player, session, failure.detail());
                                return;
                            }
                            LiveResourceNodeHarvest harvest =
                                    ((Result.Success<
                                                            LiveResourceNodeHarvest,
                                                            LiveResourceNodeErrorCode>)
                                                    harvested)
                                            .value();
                            Result<LoadedCharacterSession, CharacterSessionErrorCode> reloaded =
                                    characterSessions.reloadExternalValueMutation(session);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                activeJobs.remove(player.getUniqueId(), job);
                                                characterSessions.completeExternalValueMutation(
                                                        session,
                                                        reloaded,
                                                        completed -> {
                                                            if (!completed.isSuccess()) {
                                                                player.sendMessage(
                                                                        Component.text(
                                                                                "Harvest committed, but projection refresh failed.",
                                                                                NamedTextColor
                                                                                        .RED));
                                                                return;
                                                            }
                                                            player.sendMessage(
                                                                    Component.text(
                                                                            "Harvest committed exactly once: "
                                                                                    + service.content()
                                                                                            .outputDefinitionId()
                                                                                            .value()
                                                                                    + " x"
                                                                                    + harvest
                                                                                            .outputQuantity()
                                                                                    + " -> Pending Rewards | durability="
                                                                                    + harvest
                                                                                            .durabilityRemaining()
                                                                                    + " | rank="
                                                                                    + harvest.lifeskillState()
                                                                                            .rank()
                                                                                            .rank()
                                                                                            .displayName()
                                                                                    + " | Focus="
                                                                                    + harvest.lifeskillState()
                                                                                            .focus()
                                                                                            .focus(),
                                                                            NamedTextColor.GREEN));
                                                        });
                                            });
                        });
    }

    private void status(Player player) {
        LoadedCharacterSession session = characterSessions.active(player).orElse(null);
        if (session == null) {
            player.sendMessage(
                    Component.text("Character session is not ready.", NamedTextColor.RED));
            return;
        }
        ItemLocationRecord tool = findTool(session).orElse(null);
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<Optional<ResourceNodeStateRecord>, LiveResourceNodeErrorCode>
                                    foundNode = service.findNode();
                            Result<
                                            Optional<CharacterLifeskillStateRecord>,
                                            LiveResourceNodeErrorCode>
                                    foundCharacter = service.findCharacter(session.characterId());
                            String summary =
                                    statusSummary(session, tool, foundNode, foundCharacter);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    player.sendMessage(
                                                            Component.text(
                                                                    summary,
                                                                    NamedTextColor.LIGHT_PURPLE)));
                        });
    }

    private String statusSummary(
            LoadedCharacterSession session,
            ItemLocationRecord tool,
            Result<Optional<ResourceNodeStateRecord>, LiveResourceNodeErrorCode> foundNode,
            Result<Optional<CharacterLifeskillStateRecord>, LiveResourceNodeErrorCode>
                    foundCharacter) {
        if (foundNode instanceof Result.Failure<Optional<ResourceNodeStateRecord>, ?> failure) {
            return "Node status failed: " + failure.detail();
        }
        Optional<ResourceNodeStateRecord> node =
                ((Result.Success<Optional<ResourceNodeStateRecord>, LiveResourceNodeErrorCode>)
                                foundNode)
                        .value();
        ResourceNodePhase phase = ResourceNodePhase.AVAILABLE;
        int charges = service.content().definition().maximumCharges();
        if (node.isPresent()) {
            ResourceNodeRuntime runtime = service.decode(node.orElseThrow());
            var slot =
                    new com.branz.mmorpg.lifeskills.node.ResourceNodeEngine()
                            .slotFor(
                                    service.content().definition(), runtime, session.characterId());
            phase = slot.phase();
            charges = slot.remainingCharges();
        }
        String progression = "rank=Trainee I Focus=100";
        if (foundCharacter
                        instanceof
                        Result.Success<
                                        Optional<CharacterLifeskillStateRecord>,
                                        LiveResourceNodeErrorCode>
                                success
                && success.value().isPresent()) {
            ResourceNodeLifeskillState state = service.decode(success.value().orElseThrow());
            progression =
                    "rank=" + state.rank().rank().displayName() + " Focus=" + state.focus().focus();
        }
        String durability =
                tool == null ? "no-tool" : Integer.toString(service.toolDurability(tool));
        return "Node Lab: "
                + phase
                + " charges="
                + charges
                + " durability="
                + durability
                + " "
                + progression;
    }

    private void reconcile(boolean restart) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<Integer, LiveResourceNodeErrorCode> result =
                                    service.reconcile(clock.instant(), restart);
                            if (result
                                    instanceof
                                    Result.Failure<Integer, LiveResourceNodeErrorCode> failure) {
                                plugin.getLogger()
                                        .warning(
                                                "Resource Node reconciliation failed: "
                                                        + failure.detail());
                            }
                        });
    }

    private Optional<ItemLocationRecord> findTool(LoadedCharacterSession session) {
        return session.snapshot().itemRecords().stream()
                .filter(item -> item.definitionId().equals(service.content().toolDefinitionId()))
                .findFirst();
    }

    private void finishFailure(Player player, LoadedCharacterSession session, String detail) {
        completeFailure(player, session, detail);
    }

    private void completeFailure(Player player, LoadedCharacterSession session, String detail) {
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () ->
                                characterSessions.completeExternalValueMutation(
                                        session,
                                        Result.failure(
                                                CharacterSessionErrorCode
                                                        .CHARACTER_TRANSACTION_REJECTED,
                                                detail),
                                        ignored ->
                                                player.sendMessage(
                                                        Component.text(
                                                                "Node Lab: " + detail,
                                                                NamedTextColor.RED))));
    }

    private static int parseFocus(Player player, String[] args) {
        if (args.length < 3) {
            return 0;
        }
        try {
            int focus = Integer.parseInt(args[2]);
            if (focus < 0 || focus > 5) {
                throw new NumberFormatException("outside range");
            }
            return focus;
        } catch (NumberFormatException exception) {
            player.sendMessage(
                    Component.text("Focus cost must be from 0 to 5.", NamedTextColor.RED));
            return -1;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        activeJobs.remove(event.getPlayer().getUniqueId());
    }
}
