package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.crafting.CraftJob;
import com.branz.mmorpg.api.crafting.CraftingResult;
import com.branz.mmorpg.api.crafting.CraftingService;
import com.branz.mmorpg.api.runtime.Scheduler;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
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
import org.bukkit.event.player.PlayerJoinEvent;
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
    private final ContentService content;
    private final Scheduler scheduler;
    private final PaperItemRuntime items;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private volatile Consumer<CraftJob> completionListener = ignored -> {};

    public void completionListener(Consumer<CraftJob> listener) {
        completionListener = java.util.Objects.requireNonNull(listener, "listener");
    }

    public PaperCraftingRuntime(JavaPlugin plugin, CraftingService crafting,
                                ContentService content, Scheduler scheduler,
                                PaperItemRuntime items) {
        this.plugin = plugin;
        this.crafting = crafting;
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
        if (!busy.add(player.getUniqueId())) {
            player.sendActionBar(Component.text("Craft request already running.",
                    NamedTextColor.YELLOW));
            return;
        }
        scheduler.async(() -> crafting.begin(
                        player.getUniqueId(), recipe.id(), Set.of(station), Set.of()))
                .whenComplete((result, failure) -> scheduler.sync(() -> {
                    busy.remove(player.getUniqueId());
                    if (!player.isOnline()) return;
                    if (failure != null) {
                        player.sendActionBar(Component.text(
                                rootMessage(failure), NamedTextColor.RED));
                        return;
                    }
                    handle(player, result);
                }));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> recover(event.getPlayer()), 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        busy.remove(event.getPlayer().getUniqueId());
    }

    private void recover(Player player) {
        if (!player.isOnline() || !busy.add(player.getUniqueId())) return;
        scheduler.async(() -> crafting.activeJob(player.getUniqueId()))
                .whenComplete((job, failure) -> scheduler.sync(() -> {
                    busy.remove(player.getUniqueId());
                    if (!player.isOnline() || failure != null || job.isEmpty()) return;
                    if (job.get().status() == CraftJob.Status.PENDING_PAYMENT) {
                        resume(player, job.get());
                    } else {
                        scheduleCompletion(player, job.get());
                    }
                }));
    }

    private void resume(Player player, CraftJob job) {
        if (!busy.add(player.getUniqueId())) return;
        scheduler.async(() -> crafting.resumePayment(job.operationId()))
                .whenComplete((result, failure) -> scheduler.sync(() -> {
                    busy.remove(player.getUniqueId());
                    if (!player.isOnline()) return;
                    if (failure != null) {
                        player.sendActionBar(Component.text(
                                rootMessage(failure), NamedTextColor.RED));
                    } else {
                        handle(player, result);
                    }
                }));
    }

    private void handle(Player player, CraftingResult result) {
        switch (result.job().status()) {
            case PENDING_PAYMENT -> player.sendActionBar(Component.text(
                    "Craft escrowed; waiting for BranzWallet.", NamedTextColor.YELLOW));
            case CANCELLED -> player.sendActionBar(Component.text(
                    "Craft cancelled and inputs refunded: " + result.detail(),
                    NamedTextColor.RED));
            case IN_PROGRESS -> {
                player.sendActionBar(Component.text(
                        "Crafting " + result.job().recipeId() + "...", NamedTextColor.GREEN));
                scheduleCompletion(player, result.job());
            }
            case COMPLETE -> {
                player.sendActionBar(Component.text("Craft complete.", NamedTextColor.GREEN));
                items.reconcile(player);
                completionListener.accept(result.job());
            }
        }
    }

    private void scheduleCompletion(Player player, CraftJob job) {
        long delayMillis = Math.max(0, Duration.between(
                Instant.now(), job.readyAt().orElseThrow()).toMillis());
        long ticks = Math.max(1, (delayMillis + 49) / 50);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> complete(player, job), ticks);
    }

    private void complete(Player player, CraftJob job) {
        if (!player.isOnline() || !busy.add(player.getUniqueId())) return;
        scheduler.async(() -> crafting.complete(job.operationId()))
                .whenComplete((result, failure) -> scheduler.sync(() -> {
                    busy.remove(player.getUniqueId());
                    if (!player.isOnline()) return;
                    if (failure != null) {
                        player.sendActionBar(Component.text(
                                rootMessage(failure), NamedTextColor.RED));
                    } else if (result.job().status() == CraftJob.Status.IN_PROGRESS) {
                        scheduleCompletion(player, result.job());
                    } else {
                        handle(player, result);
                    }
                }));
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
