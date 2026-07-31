package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.items.equipment.EquipmentLoadout;
import com.branz.mmorpg.items.projection.ExpectedProjection;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import java.util.List;
import java.util.Objects;

record PersistentCharacterSnapshot(
        List<ExpectedProjection> inventory,
        EquipmentLoadout equipment,
        List<ItemLocationRecord> itemRecords,
        List<LotLocationRecord> lotRecords) {
    PersistentCharacterSnapshot {
        inventory = List.copyOf(Objects.requireNonNull(inventory, "inventory"));
        Objects.requireNonNull(equipment, "equipment");
        itemRecords = List.copyOf(Objects.requireNonNull(itemRecords, "itemRecords"));
        lotRecords = List.copyOf(Objects.requireNonNull(lotRecords, "lotRecords"));
    }
}
