package com.branz.mmorpg.items.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.ItemId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquipmentLoadoutTest {
    @Test
    void changesProduceANewImmutableLoadout() {
        EquipmentLoadout empty = EquipmentLoadout.empty();
        ItemId itemId = new ItemId(UUID.randomUUID());

        EquipmentLoadout equipped = empty.with(EquipmentSlot.NECKLACE, Optional.of(itemId));

        assertTrue(empty.item(EquipmentSlot.NECKLACE).isEmpty());
        assertEquals(itemId, equipped.item(EquipmentSlot.NECKLACE).orElseThrow());
        assertNotEquals(empty, equipped);
        assertEquals(
                EquipmentLoadout.empty(), equipped.with(EquipmentSlot.NECKLACE, Optional.empty()));
    }
}
