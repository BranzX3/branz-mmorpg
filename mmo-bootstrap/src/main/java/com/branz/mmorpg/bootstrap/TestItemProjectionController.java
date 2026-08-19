package com.branz.mmorpg.bootstrap;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/** Keeps local test projections isolated from every transfer/use path. */
final class TestItemProjectionController implements Listener {
    private static final int PHYSICAL_SHIELD_D13_UNEQUIP_HOTBAR_SLOT = 6;
    private static final String PHYSICAL_CONSUMABLE_ACCEPTANCE_PROPERTY =
            "mmo.physical-consumable-lot-acceptance";
    private static final String PHYSICAL_CONSUMABLE_C4_ACCEPTANCE_PROPERTY =
            "mmo.physical-consumable-c4-acceptance";
    private static final String PHYSICAL_SHIELD_D13_ACCEPTANCE_PROPERTY =
            "mmo.physical-shield-d13-acceptance";
    private static final String PHYSICAL_SHIELD_D46_ACCEPTANCE_PROPERTY =
            "mmo.physical-shield-d46-acceptance";

    private final TestItemProjectionService projections;

    TestItemProjectionController(TestItemProjectionService projections) {
        this.projections = Objects.requireNonNull(projections, "projections");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack hotbar =
                event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
                        ? player.getInventory().getItem(event.getHotbarButton())
                        : null;
        if (blocksInventoryAcceptancePath(event.getCurrentItem())
                || blocksInventoryAcceptancePath(event.getCursor())
                || blocksInventoryAcceptancePath(hotbar)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (blocksPhysicalAcceptancePath(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isTest(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (blocksShieldSwapAcceptancePath(event.getMainHandItem())
                || blocksShieldSwapAcceptancePath(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (blocksPhysicalAcceptancePath(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (blocksPhysicalAcceptancePath(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isTest);
        projections.removeAll(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        projections.removeAll(player);
        if (Boolean.getBoolean(PHYSICAL_SHIELD_D13_ACCEPTANCE_PROPERTY)) {
            player.getInventory().setItem(PHYSICAL_SHIELD_D13_UNEQUIP_HOTBAR_SLOT, null);
            player.getInventory().setHeldItemSlot(PHYSICAL_SHIELD_D13_UNEQUIP_HOTBAR_SLOT);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        projections.removeAll(event.getPlayer());
    }

    private boolean blocksInventoryAcceptancePath(ItemStack item) {
        if (!isTest(item)) {
            return false;
        }
        boolean shieldAllowed =
                Boolean.getBoolean(PHYSICAL_SHIELD_D13_ACCEPTANCE_PROPERTY)
                        && projections.isPhysicalShieldD13AcceptanceProjection(item);
        return !shieldAllowed && blocksPhysicalAcceptancePath(item);
    }

    private boolean blocksPhysicalAcceptancePath(ItemStack item) {
        if (!isTest(item)) {
            return false;
        }
        boolean c12Allowed =
                Boolean.getBoolean(PHYSICAL_CONSUMABLE_ACCEPTANCE_PROPERTY)
                        && projections.isPhysicalConsumableAcceptanceProjection(item);
        boolean c4Allowed =
                Boolean.getBoolean(PHYSICAL_CONSUMABLE_C4_ACCEPTANCE_PROPERTY)
                        && projections.isPhysicalConsumableC4AcceptanceProjection(item);
        return !c12Allowed && !c4Allowed;
    }

    private boolean blocksShieldSwapAcceptancePath(ItemStack item) {
        if (!isTest(item)) {
            return false;
        }
        boolean shieldAllowed =
                Boolean.getBoolean(PHYSICAL_SHIELD_D13_ACCEPTANCE_PROPERTY)
                        && projections.isPhysicalShieldD13AcceptanceProjection(item);
        boolean staffAllowed =
                Boolean.getBoolean(PHYSICAL_SHIELD_D46_ACCEPTANCE_PROPERTY)
                        && projections.isPhysicalShieldD46StaffAcceptanceProjection(item);
        return !shieldAllowed && !staffAllowed;
    }

    private boolean isTest(ItemStack item) {
        return projections.isTestProjection(item);
    }
}
