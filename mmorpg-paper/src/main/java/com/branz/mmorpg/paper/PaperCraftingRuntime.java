package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.crafting.CraftJob;
import com.branz.mmorpg.api.crafting.CraftingResult;
import com.branz.mmorpg.api.crafting.CraftingService;
import com.branz.mmorpg.api.player.SessionToken;
import com.branz.mmorpg.api.runtime.Scheduler;
import com.branz.mmorpg.core.player.PlayerSessionService;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

/** Shift-right-click crafting-station adapter with restart recovery. */
public final class PaperCraftingRuntime implements Listener {
    private static final Map<Material, String> STATIONS = Map.of(
            Material.SMITHING_TABLE, "branz:forge",
            Material.BREWING_STAND, "branz:alchemy_station",
            Material.LOOM, "branz:tailoring_station",
            Material.SMOKER, "branz:cooking_station",
            Material.ENCHANTING_TABLE, "branz:enchanting_station");

    private final JavaPlugin plugin;
    private final CraftingService crafting;
    private final PlayerSessionService sessions;
    private final ContentService content;
    private final Scheduler scheduler;
    private final PaperItemRuntime items;
    private final Map<UUID, SessionToken> busy = new ConcurrentHashMap<>();
    private volatile Consumer<CraftJob> completionListener = ignored -> {};

    public void completionListener(Consumer<CraftJob> listener) {
        completionListener = java.util.Objects.requireNonNull(listener, "listener");
    }

    public PaperCraftingRuntime(JavaPlugin plugin, CraftingService crafting,
                                PlayerSessionService sessions,
                                ContentService content, Scheduler scheduler,
                                PaperItemRuntime items) {
        this.plugin = plugin;
        this.crafting = crafting;
        this.sessions = sessions;
        this.content = content;
        this.scheduler = scheduler;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND
                || !event.getPlayer().isSneaking()
                || event.getClickedBlock() == null) return;
        String station = STATIONS.get(event.getClickedBlock().getType());
        if (station == null) return;
        var recipe = content.snapshot().recipes().values().stream()
                .filter(candidate -> candidate.stationTag().equals(station))
                .sorted(Comparator.comparing(candidate -> candidate.id().toString()))
                .findFirst().orElse(null);
        if (recipe == null) {
            event.getPlayer().sendActionBar(Component.text(
                    "No recipe is configured for this station.", NamedTextColor.YELLOW));
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        SessionToken token;
        try {
            token = sessions.requirePlayable(player.getUniqueId()).token();
        } catch (RuntimeException unavailable) {
            player.sendActionBar(Component.text(
                    "MMO profile is not ready.", NamedTextColor.RED));
            return;
        }
        if (!beginWork(token)) {
            player.sendActionBar(Component.text("Craft request already running.",
                    NamedTextColor.YELLOW));
            return;
        }
        scheduler.async(() -> crafting.begin(
                        player.getUniqueId(), recipe.id(), java.util.Set.of(station),
                        java.util.Set.of()))
                .whenComplete((result, failure) -> scheduler.sync(() -> {
                    endWork(token);
                    if (!player.isOnline() || !sessions.isLive(token)) return;
                    if (failure != null) {
                        player.sendActionBar(Component.text(
                                rootMessage(failure), NamedTextColor.RED));
                        return;
                    }
                    handle(player, result, token);
                }));
    }

    /** Called only after the authoritative Player Session becomes ACTIVE. */
    public void sessionReady(UUID playerId) {
        SessionToken token = sessions.requirePlayable(playerId).token();
        if (!beginWork(token)) return;
        scheduler.async(() -> crafting.activeJob(playerId))
                .whenComplete((job, failure) -> scheduler.sync(() -> {
                    endWork(token);
                    if (!sessions.isLive(token) || failure != null || job.isEmpty()) return;
                    Player player = plugin.getServer().getPlayer(playerId);
                    if (player == null || !player.isOnline()) return;
                    if (job.get().status() == CraftJob.Status.PENDING_PAYMENT) {
                        resume(player, job.get(), token);
                    } else {
                        scheduleCompletion(player, job.get(), token);
                    }
                }));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        busy.remove(event.getPlayer().getUniqueId());
    }

    private void resume(Player player, CraftJob job, SessionToken token) {
        if (!beginWork(token)) return;
        scheduler.async(() -> crafting.resumePayment(job.operationId()))
                .whenComplete((result, failure) -> scheduler.sync(() -> {
                    endWork(token);
                    if (!player.isOnline() || !sessions.isLive(token)) return;
                    if (failure != null) {
                        player.sendActionBar(Component.text(
                                rootMessage(failure), NamedTextColor.RED));
                    } else {
                        handle(player, result, token);
                    }
                }));
    }

    private void handle(Player player, CraftingResult result, SessionToken token) {
        switch (result.job().status()) {
            case PENDING_PAYMENT -> player.sendActionBar(Component.text(
                    "Craft escrowed; waiting for BranzWallet.", NamedTextColor.YELLOW));
            case CANCELLED -> player.sendActionBar(Component.text(
                    "Craft cancelled and inputs refunded: " + result.detail(),
                    NamedTextColor.RED));
            case IN_PROGRESS -> {
                player.sendActionBar(Component.text(
                        "Crafting " + result.job().recipeId() + "...", NamedTextColor.GREEN));
                scheduleCompletion(player, result.job(), token);
            }
            case COMPLETE -> {
                player.sendActionBar(Component.text("Craft complete.", NamedTextColor.GREEN));
                items.reconcile(player);
                completionListener.accept(result.job());
            }
        }
    }

    private void scheduleCompletion(Player player, CraftJob job, SessionToken token) {
        long delayMillis = Math.max(0, Duration.between(
                Instant.now(), job.readyAt().orElseThrow()).toMillis());
        long ticks = Math.max(1, (delayMillis + 49) / 50);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> complete(player, job, token), ticks);
    }

    private void complete(Player player, CraftJob job, SessionToken token) {
        if (!player.isOnline() || !sessions.isLive(token) || !beginWork(token)) return;
        scheduler.async(() -> crafting.complete(job.operationId()))
                .whenComplete((result, failure) -> scheduler.sync(() -> {
                    endWork(token);
                    if (!player.isOnline() || !sessions.isLive(token)) return;
                    if (failure != null) {
                        player.sendActionBar(Component.text(
                                rootMessage(failure), NamedTextColor.RED));
                    } else if (result.job().status() == CraftJob.Status.IN_PROGRESS) {
                        scheduleCompletion(player, result.job(), token);
                    } else {
                        handle(player, result, token);
                    }
                }));
    }

    private boolean beginWork(SessionToken token) {
        return busy.putIfAbsent(token.playerId(), token) == null;
    }

    private void endWork(SessionToken token) {
        busy.remove(token.playerId(), token);
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
