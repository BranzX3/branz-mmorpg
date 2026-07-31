package com.branz.mmorpg.bootstrap;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Recognizes persisted dev-provenance projections and keeps them outside normal transfer/use flows.
 * The database row remains authoritative when its Bukkit projection is cleared.
 */
final class TestItemProjectionService {
    private final BukkitItemProjectionCodec codec;

    TestItemProjectionService(BukkitItemProjectionCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    boolean isTestProjection(ItemStack item) {
        return codec.isTestProjection(item);
    }

    void removeAll(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        boolean storageChanged = false;
        for (int slot = 0; slot < storage.length; slot++) {
            if (isTestProjection(storage[slot])) {
                storage[slot] = null;
                storageChanged = true;
            }
        }
        if (storageChanged) {
            inventory.setStorageContents(storage);
        }
        if (isTestProjection(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
        ItemStack[] armor = inventory.getArmorContents();
        boolean armorChanged = false;
        for (int slot = 0; slot < armor.length; slot++) {
            if (isTestProjection(armor[slot])) {
                armor[slot] = null;
                armorChanged = true;
            }
        }
        if (armorChanged) {
            inventory.setArmorContents(armor);
        }
    }
}
