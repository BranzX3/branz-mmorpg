package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuiverAmmoLotsTest {
    private static final DefinitionId ARROW = DefinitionId.of("ammo.test.arrow");
    private static final CharacterId OWNER = new CharacterId(UUID.randomUUID());
    private static final ItemId QUIVER = new ItemId(UUID.randomUUID());
    private static final ItemId OTHER_QUIVER = new ItemId(UUID.randomUUID());

    @Test
    void selectsByStableLotUuidAndCountsOnlyTheNamedQuiver() {
        LotLocationRecord later =
                lot("00000000-0000-0000-0000-000000000020", ARROW, 4, ValueLocation.quiver(QUIVER));
        LotLocationRecord first =
                lot("00000000-0000-0000-0000-000000000010", ARROW, 2, ValueLocation.quiver(QUIVER));
        LotLocationRecord other =
                lot(
                        "00000000-0000-0000-0000-000000000001",
                        ARROW,
                        50,
                        ValueLocation.quiver(OTHER_QUIVER));
        LotLocationRecord inventory =
                lot(
                        "00000000-0000-0000-0000-000000000002",
                        ARROW,
                        30,
                        ValueLocation.inventory("slot:2"));

        List<LotLocationRecord> records = List.of(later, other, inventory, first);
        assertEquals(
                first.lotId(), QuiverAmmoLots.select(records, QUIVER, ARROW).orElseThrow().lotId());
        assertEquals(6, QuiverAmmoLots.quantity(records, QUIVER, ARROW));
        assertEquals(6, QuiverAmmoLots.usedCapacity(records, QUIVER));
        assertEquals(50, QuiverAmmoLots.usedCapacity(records, OTHER_QUIVER));
    }

    private static LotLocationRecord lot(
            String lotUuid, DefinitionId definitionId, long quantity, ValueLocation location) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new LotLocationRecord(
                new LotId(UUID.fromString(lotUuid)),
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
