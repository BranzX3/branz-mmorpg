package com.branz.mmorpg.scenes;

import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.items.quiver.QuiverPreparation;
import java.util.Objects;
import java.util.Optional;

public record ScenePreviewState(
        EquipmentLoadout equipment,
        QuiverPreparation quiverPreparation,
        Optional<QuiverAmmoTransferPreview> quiverTransfer) {
    public ScenePreviewState {
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(quiverPreparation, "quiverPreparation");
        Objects.requireNonNull(quiverTransfer, "quiverTransfer");
    }

    public ScenePreviewState(EquipmentLoadout equipment, QuiverPreparation quiverPreparation) {
        this(equipment, quiverPreparation, Optional.empty());
    }

    public ScenePreviewState(EquipmentLoadout equipment) {
        this(equipment, QuiverPreparation.empty());
    }
}
