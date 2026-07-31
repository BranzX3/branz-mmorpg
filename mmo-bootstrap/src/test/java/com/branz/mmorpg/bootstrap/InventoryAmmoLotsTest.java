package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryAmmoLotsTest {
    private static final DefinitionId ARROW = DefinitionId.of("ammo.test.arrow");
    private static final DefinitionId BOLT = DefinitionId.of("ammo.test.bolt");
    private static final CharacterId OWNER = new CharacterId(UUID.randomUUID());

    @Test
    void selectsEarliestInventoryLotAndCountsOnlyExactAmmoDefinition() {
        LotLocationRecord later = lot(ARROW, 4, ValueLocation.inventory("slot:10"));
        LotLocationRecord first = lot(ARROW, 2, ValueLocation.inventory("slot:2"));
        LotLocationRecord destroyed = lot(ARROW, 0, ValueLocation.destroyed("spent"));
        LotLocationRecord otherAmmo = lot(BOLT, 20, ValueLocation.inventory("slot:1"));
        List<LotLocationRecord> records = List.of(later, destroyed, otherAmmo, first);

        assertEquals(first.lotId(), InventoryAmmoLots.select(records, ARROW).orElseThrow().lotId());
        assertEquals(6, InventoryAmmoLots.quantity(records, ARROW));
        assertEquals(20, InventoryAmmoLots.quantity(records, BOLT));
    }

    @Test
    void rejectsDestroyedEmptyAndDifferentAmmoLots() {
        List<LotLocationRecord> records =
                List.of(
                        lot(ARROW, 0, ValueLocation.inventory("slot:2")),
                        lot(ARROW, 4, ValueLocation.destroyed("spent")),
                        lot(BOLT, 4, ValueLocation.inventory("slot:1")));

        assertEquals(Optional.empty(), InventoryAmmoLots.select(records, ARROW));
        assertEquals(0, InventoryAmmoLots.quantity(records, ARROW));
    }

    private static LotLocationRecord lot(
            DefinitionId definitionId, long quantity, ValueLocation location) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new LotLocationRecord(
                new LotId(UUID.randomUUID()),
                definitionId,
                "test",
                quantity,
                Optional.of(OWNER),
                location,
                "{}",
                "content-test-1",
                1,
                new TransactionId(UUID.randomUUID()),
                now,
                now);
    }
}
