package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.util.Objects;
import java.util.OptionalInt;

/** Stable read-only lines for local physical-authority acceptance evidence. */
final class PhysicalAuthorityInspectionFormatter {
    private PhysicalAuthorityInspectionFormatter() {}

    static String item(ItemLocationRecord record, OptionalInt baseMaximumDurability) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(baseMaximumDurability, "baseMaximumDurability");
        return "ITEM uuid="
                + record.itemId().value()
                + " def="
                + record.definitionId().value()
                + " loc="
                + location(record.location())
                + " ver="
                + record.version()
                + " durability="
                + durability(record.payloadJson(), baseMaximumDurability)
                + " tx="
                + record.lastTransactionId().value()
                + " content="
                + record.contentVersion();
    }

    static String lot(LotLocationRecord record) {
        Objects.requireNonNull(record, "record");
        return "LOT uuid="
                + record.lotId().value()
                + " def="
                + record.definitionId().value()
                + " loc="
                + location(record.location())
                + " ver="
                + record.version()
                + " qty="
                + record.quantity()
                + " tx="
                + record.lastTransactionId().value()
                + " content="
                + record.contentVersion();
    }

    private static String durability(String payloadJson, OptionalInt baseMaximumDurability) {
        if (baseMaximumDurability.isEmpty()) {
            return "n/a";
        }
        try {
            ItemDurability state =
                    ItemDurabilityPayloadCodec.decode(
                            payloadJson, baseMaximumDurability.getAsInt());
            return state.current() + "/" + state.maximum();
        } catch (IllegalArgumentException exception) {
            return "INVALID";
        }
    }

    private static String location(ValueLocation location) {
        return location.type().name()
                + location.reference().map(reference -> "/" + reference).orElse("");
    }
}
