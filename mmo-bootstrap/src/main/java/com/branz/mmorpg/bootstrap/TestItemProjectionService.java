package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.items.projection.ProjectionValueType;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Recognizes persisted dev-provenance projections and keeps them outside normal transfer/use flows.
 * The database row remains authoritative when its Bukkit projection is cleared.
 */
final class TestItemProjectionService {
    private static final String PHYSICAL_CONSUMABLE_ACCEPTANCE_DEFINITION =
            "consumable.training_body_tonic";
    private static final String PHYSICAL_CONSUMABLE_C4_ACCEPTANCE_DEFINITION =
            "consumable.training_weapon_coating";

    private final BukkitItemProjectionCodec codec;

    TestItemProjectionService(BukkitItemProjectionCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    boolean isTestProjection(ItemStack item) {
        return codec.isTestProjection(item);
    }

    boolean isPhysicalConsumableAcceptanceProjection(ItemStack item) {
        return isExactStackableLot(item, PHYSICAL_CONSUMABLE_ACCEPTANCE_DEFINITION);
    }

    boolean isPhysicalConsumableC4AcceptanceProjection(ItemStack item) {
        return isExactStackableLot(item, PHYSICAL_CONSUMABLE_C4_ACCEPTANCE_DEFINITION);
    }

    private boolean isExactStackableLot(ItemStack item, String definitionId) {
        if (!isTestProjection(item)) {
            return false;
        }
        return codec.decode(item, 0)
                .filter(projection -> projection.signatureValid())
                .filter(projection -> projection.valueType() == ProjectionValueType.STACKABLE_LOT)
                .filter(projection -> definitionId.equals(projection.definitionId().value()))
                .isPresent();
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
