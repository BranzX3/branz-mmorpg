package com.branz.mmorpg.bootstrap;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

final class ChronicleService {
    static final int HOTBAR_SLOT = 8;
    private static final String CHRONICLE_MARKER = "adventurers_chronicle_v1";

    private final NamespacedKey systemItemKey;

    ChronicleService(JavaPlugin plugin) {
        systemItemKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "system_item");
    }

    ItemStack createChronicle() {
        ItemStack chronicle = new ItemStack(Material.WRITTEN_BOOK);
        chronicle.editMeta(
                meta -> {
                    meta.displayName(Component.text("Adventurer's Chronicle", NamedTextColor.GOLD));
                    meta.lore(
                            List.of(
                                    Component.text(
                                            "Right-click to open the Scene Hub",
                                            NamedTextColor.GRAY),
                                    Component.text(
                                            "Permanent system item", NamedTextColor.DARK_GRAY)));
                    meta.getPersistentDataContainer()
                            .set(systemItemKey, PersistentDataType.STRING, CHRONICLE_MARKER);
                });
        return chronicle;
    }

    boolean isChronicle(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return CHRONICLE_MARKER.equals(
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(systemItemKey, PersistentDataType.STRING));
    }

    ChronicleReconcileOutcome reconcile(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        ItemStack slotNine = storage[HOTBAR_SLOT];
        List<ChronicleSlotState> states =
                java.util.Arrays.stream(storage).map(this::slotState).toList();
        ChroniclePlacementPlan plan = ChroniclePlacementPlanner.plan(states, HOTBAR_SLOT);

        if (isChronicle(slotNine)) {
            removeStorageDuplicates(inventory, plan.duplicateChronicleSlots());
            removeEquipmentDuplicates(inventory);
            return plan.outcome();
        }
        if (plan.outcome() == ChronicleReconcileOutcome.NO_SPACE) {
            return plan.outcome();
        }

        int sourceSlot = plan.sourceChronicleSlot().orElse(-1);
        ItemStack existingChronicle = sourceSlot >= 0 ? inventory.getItem(sourceSlot) : null;
        int relocatedDestination = plan.displacedValueDestination().orElse(-1);
        if (relocatedDestination >= 0) {
            int destination = relocatedDestination;
            inventory.setItem(destination, slotNine);
        }

        ItemStack chronicle = existingChronicle != null ? existingChronicle : createChronicle();
        inventory.setItem(HOTBAR_SLOT, chronicle);
        if (sourceSlot >= 0 && sourceSlot != HOTBAR_SLOT && relocatedDestination != sourceSlot) {
            inventory.setItem(sourceSlot, null);
        }
        removeStorageDuplicates(inventory, plan.duplicateChronicleSlots());
        removeEquipmentDuplicates(inventory);
        return plan.outcome();
    }

    private void removeStorageDuplicates(PlayerInventory inventory, List<Integer> duplicateSlots) {
        for (int slot : duplicateSlots) {
            if (isChronicle(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
    }

    private ChronicleSlotState slotState(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return ChronicleSlotState.EMPTY;
        }
        return isChronicle(item) ? ChronicleSlotState.CHRONICLE : ChronicleSlotState.VALUE;
    }

    private void removeEquipmentDuplicates(PlayerInventory inventory) {
        if (isChronicle(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
        ItemStack[] armor = inventory.getArmorContents();
        boolean changed = false;
        for (int index = 0; index < armor.length; index++) {
            if (isChronicle(armor[index])) {
                armor[index] = null;
                changed = true;
            }
        }
        if (changed) {
            inventory.setArmorContents(armor);
        }
    }
}
