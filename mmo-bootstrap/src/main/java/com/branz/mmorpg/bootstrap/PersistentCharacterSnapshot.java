package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.items.projection.ExpectedProjection;
import com.branz.mmorpg.items.quiver.QuiverPreparation;
import com.branz.mmorpg.persistence.progression.ProgressionTrackRecord;
import com.branz.mmorpg.persistence.transaction.CharacterBuildRecord;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.progression.build.CharacterBuild;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

record PersistentCharacterSnapshot(
        List<ExpectedProjection> inventory,
        EquipmentLoadout equipment,
        QuiverPreparation quiverPreparation,
        CharacterBuild build,
        Optional<CharacterBuildRecord> buildRecord,
        List<ProgressionTrackRecord> progressionTracks,
        List<ItemLocationRecord> itemRecords,
        List<LotLocationRecord> lotRecords) {
    PersistentCharacterSnapshot {
        inventory = List.copyOf(Objects.requireNonNull(inventory, "inventory"));
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(quiverPreparation, "quiverPreparation");
        Objects.requireNonNull(build, "build");
        Objects.requireNonNull(buildRecord, "buildRecord");
        progressionTracks =
                List.copyOf(Objects.requireNonNull(progressionTracks, "progressionTracks"));
        itemRecords = List.copyOf(Objects.requireNonNull(itemRecords, "itemRecords"));
        lotRecords = List.copyOf(Objects.requireNonNull(lotRecords, "lotRecords"));
    }
}
