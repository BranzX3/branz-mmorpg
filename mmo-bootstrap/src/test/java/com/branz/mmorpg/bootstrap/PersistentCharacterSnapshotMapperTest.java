package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.items.projection.ProjectionValueType;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersistentCharacterSnapshotMapperTest {
    private static final CharacterId CHARACTER = new CharacterId(UUID.randomUUID());
    private static final TransactionId TRANSACTION = new TransactionId(UUID.randomUUID());
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void mapsInventoryLotsAndNativeEquipmentFromDatabaseTruth() {
        ItemId inventoryItem = new ItemId(UUID.randomUUID());
        ItemId equippedItem = new ItemId(UUID.randomUUID());
        LotId lotId = new LotId(UUID.randomUUID());

        PersistentCharacterSnapshot snapshot =
                PersistentCharacterSnapshotMapper.map(
                        List.of(
                                item(
                                        inventoryItem,
                                        ValueLocation.inventory("slot:2"),
                                        "{\"displayRevision\":3}"),
                                item(
                                        equippedItem,
                                        ValueLocation.nativeEquipped("MAIN_HAND"),
                                        "{}")),
                        List.of(lot(lotId, ValueLocation.inventory("slot:4"))));

        assertEquals(2, snapshot.inventory().size());
        assertEquals(inventoryItem.value(), snapshot.inventory().get(0).valueId());
        assertEquals(3, snapshot.inventory().get(0).displayRevision());
        assertEquals(ProjectionValueType.STACKABLE_LOT, snapshot.inventory().get(1).valueType());
        assertTrue(snapshot.equipment().item(EquipmentSlot.MAIN_HAND).isPresent());
        assertEquals(
                equippedItem, snapshot.equipment().item(EquipmentSlot.MAIN_HAND).orElseThrow());
    }

    @Test
    void rejectsChronicleSlotAndInvalidPersistentJson() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PersistentCharacterSnapshotMapper.map(
                                List.of(
                                        item(
                                                new ItemId(UUID.randomUUID()),
                                                ValueLocation.inventory("slot:8"),
                                                "{}")),
                                List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PersistentCharacterSnapshotMapper.map(
                                List.of(
                                        item(
                                                new ItemId(UUID.randomUUID()),
                                                ValueLocation.inventory("slot:2"),
                                                "{broken")),
                                List.of()));
    }

    private static ItemLocationRecord item(ItemId itemId, ValueLocation location, String payload) {
        return new ItemLocationRecord(
                itemId,
                DefinitionId.of("item.test.relic"),
                Optional.of(CHARACTER),
                location,
                payload,
                "content.test.1",
                1,
                TRANSACTION,
                CREATED,
                CREATED);
    }

    private static LotLocationRecord lot(LotId lotId, ValueLocation location) {
        return new LotLocationRecord(
                lotId,
                DefinitionId.of("material.test.ore"),
                "dev-test",
                7,
                Optional.of(CHARACTER),
                location,
                "{\"testProvenance\":\"dev:test\"}",
                "content.test.1",
                2,
                TRANSACTION,
                CREATED,
                CREATED);
    }
}
