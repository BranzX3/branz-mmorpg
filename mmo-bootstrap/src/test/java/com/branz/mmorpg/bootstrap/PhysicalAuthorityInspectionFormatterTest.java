package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalAuthorityInspectionFormatterTest {
    private static final CharacterId CHARACTER_ID =
            new CharacterId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final TransactionId TRANSACTION_ID =
            new TransactionId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    void formatsDurableItemWithoutExposingPayload() {
        ItemLocationRecord record =
                new ItemLocationRecord(
                        new ItemId(UUID.fromString("00000000-0000-0000-0000-000000000003")),
                        DefinitionId.of("weapon.training_sword"),
                        Optional.of(CHARACTER_ID),
                        ValueLocation.inventory("slot:4"),
                        "{\"displayRevision\":2,\"secretMarker\":\"must-not-leak\",\"durability\":{\"currentDurability\":73,\"maximumDurability\":120}}",
                        "1.0.0-test",
                        7,
                        TRANSACTION_ID,
                        NOW,
                        NOW);

        assertEquals(
                "ITEM uuid=00000000-0000-0000-0000-000000000003 def=weapon.training_sword "
                        + "loc=CHARACTER_INVENTORY/slot:4 ver=7 durability=73/120 "
                        + "tx=00000000-0000-0000-0000-000000000002 content=1.0.0-test",
                PhysicalAuthorityInspectionFormatter.item(record, OptionalInt.of(120)));
    }

    @Test
    void malformedDurabilityFailsClosedInInspectionOutput() {
        ItemLocationRecord record =
                new ItemLocationRecord(
                        new ItemId(UUID.fromString("00000000-0000-0000-0000-000000000004")),
                        DefinitionId.of("equipment.training_shield"),
                        Optional.of(CHARACTER_ID),
                        ValueLocation.nativeEquipped("OFF_HAND"),
                        "{\"durability\":\"bad\"}",
                        "1.0.0-test",
                        3,
                        TRANSACTION_ID,
                        NOW,
                        NOW);

        assertEquals(
                "ITEM uuid=00000000-0000-0000-0000-000000000004 def=equipment.training_shield "
                        + "loc=NATIVE_EQUIPPED/OFF_HAND ver=3 durability=INVALID "
                        + "tx=00000000-0000-0000-0000-000000000002 content=1.0.0-test",
                PhysicalAuthorityInspectionFormatter.item(record, OptionalInt.of(180)));
    }

    @Test
    void formatsLotQuantityAndLocation() {
        LotLocationRecord record =
                new LotLocationRecord(
                        new LotId(UUID.fromString("00000000-0000-0000-0000-000000000005")),
                        DefinitionId.of("consumable.training_body_tonic"),
                        "default",
                        63,
                        Optional.of(CHARACTER_ID),
                        ValueLocation.inventory("slot:6"),
                        "{}",
                        "1.0.0-test",
                        9,
                        TRANSACTION_ID,
                        NOW,
                        NOW);

        assertEquals(
                "LOT uuid=00000000-0000-0000-0000-000000000005 def=consumable.training_body_tonic "
                        + "loc=CHARACTER_INVENTORY/slot:6 ver=9 qty=63 "
                        + "tx=00000000-0000-0000-0000-000000000002 content=1.0.0-test",
                PhysicalAuthorityInspectionFormatter.lot(record));
    }
}
