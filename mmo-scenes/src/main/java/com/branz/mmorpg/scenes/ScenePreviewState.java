package com.branz.mmorpg.scenes;

import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import java.util.Objects;

public record ScenePreviewState(EquipmentLoadout equipment) {
    public ScenePreviewState {
        Objects.requireNonNull(equipment, "equipment");
    }
}
