package com.branz.mmorpg.scenes;

import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.items.quiver.QuiverPreparation;
import com.branz.mmorpg.progression.build.CharacterBuild;
import java.util.Objects;
import java.util.Optional;

public record ScenePreviewState(
        EquipmentLoadout equipment,
        QuiverPreparation quiverPreparation,
        Optional<QuiverAmmoTransferPreview> quiverTransfer,
        CharacterBuild build) {
    public ScenePreviewState {
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(quiverPreparation, "quiverPreparation");
        Objects.requireNonNull(quiverTransfer, "quiverTransfer");
        Objects.requireNonNull(build, "build");
    }

    public ScenePreviewState(
            EquipmentLoadout equipment,
            QuiverPreparation quiverPreparation,
            Optional<QuiverAmmoTransferPreview> quiverTransfer) {
        this(equipment, quiverPreparation, quiverTransfer, CharacterBuild.initial());
    }

    public ScenePreviewState(EquipmentLoadout equipment, QuiverPreparation quiverPreparation) {
        this(equipment, quiverPreparation, Optional.empty(), CharacterBuild.initial());
    }

    public ScenePreviewState(
            EquipmentLoadout equipment, QuiverPreparation quiverPreparation, CharacterBuild build) {
        this(equipment, quiverPreparation, Optional.empty(), build);
    }

    public ScenePreviewState(EquipmentLoadout equipment) {
        this(equipment, QuiverPreparation.empty(), Optional.empty(), CharacterBuild.initial());
    }
}
