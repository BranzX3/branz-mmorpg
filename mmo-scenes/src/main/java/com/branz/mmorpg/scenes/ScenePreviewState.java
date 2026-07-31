package com.branz.mmorpg.scenes;

import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.items.quiver.QuiverPreparation;
import java.util.Objects;

public record ScenePreviewState(EquipmentLoadout equipment, QuiverPreparation quiverPreparation) {
    public ScenePreviewState {
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(quiverPreparation, "quiverPreparation");
    }

    public ScenePreviewState(EquipmentLoadout equipment) {
        this(equipment, QuiverPreparation.empty());
    }
}
