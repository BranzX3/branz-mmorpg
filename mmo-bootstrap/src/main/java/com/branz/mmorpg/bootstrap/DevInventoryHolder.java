package com.branz.mmorpg.bootstrap;

import java.util.Objects;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class DevInventoryHolder implements InventoryHolder {
    enum Page {
        HUB,
        CONTENT,
        ITEM_SPAWNER
    }

    private final Page page;
    private Inventory inventory;

    DevInventoryHolder(Page page) {
        this.page = Objects.requireNonNull(page, "page");
    }

    Page page() {
        return page;
    }

    void attach(Inventory inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("inventory has not been attached");
        }
        return inventory;
    }
}
