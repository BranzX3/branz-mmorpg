package com.branz.mmorpg.bootstrap;

import java.util.Objects;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

final class ChronicleController implements Listener {
    private final JavaPlugin plugin;
    private final ChronicleService chronicle;
    private final Predicate<Player> admissionReady;

    ChronicleController(
            JavaPlugin plugin, ChronicleService chronicle, Predicate<Player> admissionReady) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.chronicle = Objects.requireNonNull(chronicle, "chronicle");
        this.admissionReady = Objects.requireNonNull(admissionReady, "admissionReady");
    }

    void reconcile(Player player) {
        if (!admissionReady.test(player)) {
            return;
        }
        ChronicleReconcileOutcome outcome = chronicle.reconcile(player);
        if (outcome == ChronicleReconcileOutcome.NO_SPACE) {
            player.sendMessage(
                    Component.text(
                            "Free one inventory slot so the Chronicle can be restored safely.",
                            NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onC12DiagnosticBefore(InventoryClickEvent event) {
        logC12Diagnostic("LOWEST", event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean slotNine =
                event.getClickedInventory() instanceof PlayerInventory
                        && event.getSlot() == ChronicleService.HOTBAR_SLOT;
        boolean hotbarSwap =
                event.getClick() == ClickType.NUMBER_KEY
                        && event.getHotbarButton() == ChronicleService.HOTBAR_SLOT;
        if (slotNine
                || hotbarSwap
                || chronicle.isChronicle(event.getCurrentItem())
                || chronicle.isChronicle(event.getCursor())) {
            event.setCancelled(true);
            Component message =
                    Component.text(
                            "This MMO inventory action is not available in the physical-item slice yet.",
                            NamedTextColor.RED);
            player.sendActionBar(message);
            player.sendMessage(message);
            scheduleReconcile(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onC12DiagnosticAfter(InventoryClickEvent event) {
        logC12Diagnostic("MONITOR", event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean touchesSlotNine =
                event.getRawSlots().stream()
                        .anyMatch(
                                rawSlot ->
                                        event.getView().getInventory(rawSlot)
                                                        instanceof PlayerInventory
                                                && event.getView().convertSlot(rawSlot)
                                                        == ChronicleService.HOTBAR_SLOT);
        if (touchesSlotNine || chronicle.isChronicle(event.getOldCursor())) {
            event.setCancelled(true);
            scheduleReconcile(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (chronicle.isChronicle(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            scheduleReconcile(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (chronicle.isChronicle(event.getMainHandItem())
                || chronicle.isChronicle(event.getOffHandItem())) {
            event.setCancelled(true);
            scheduleReconcile(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (chronicle.isChronicle(event.getItem())) {
            event.setCancelled(true);
            scheduleReconcile(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(chronicle::isChronicle);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleReconcile(event.getPlayer());
    }

    private static void logC12Diagnostic(String phase, InventoryClickEvent event) {
        String clicked =
                event.getClickedInventory() == null
                        ? "null"
                        : event.getClickedInventory().getType().name();
        String current =
                event.getCurrentItem() == null
                        ? "null"
                        : event.getCurrentItem().getType().name();
        String cursor =
                event.getCursor() == null ? "null" : event.getCursor().getType().name();
        System.out.println(
                "PHYSICAL_AUTHORITY_C12_DIAG_EVENT_SERVER phase="
                        + phase
                        + " cancelled="
                        + event.isCancelled()
                        + " action="
                        + event.getAction().name()
                        + " click="
                        + event.getClick().name()
                        + " rawSlot="
                        + event.getRawSlot()
                        + " slot="
                        + event.getSlot()
                        + " clicked="
                        + clicked
                        + " current="
                        + current
                        + " cursor="
                        + cursor);
    }

    private void scheduleReconcile(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> reconcile(player));
    }
}
