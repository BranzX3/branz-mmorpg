package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.scenes.SceneMode;
import com.branz.mmorpg.scenes.SceneSessionId;
import java.util.Objects;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class SceneInventoryHolder implements InventoryHolder {
    private final SceneSessionId sessionId;
    private final SceneMode mode;
    private Inventory inventory;

    SceneInventoryHolder(SceneSessionId sessionId, SceneMode mode) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    SceneSessionId sessionId() {
        return sessionId;
    }

    SceneMode mode() {
        return mode;
    }

    void attach(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("inventory already attached");
        }
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
