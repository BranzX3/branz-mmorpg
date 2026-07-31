package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic authoritative view of lots stored by one equipped Quiver item UUID. */
final class QuiverAmmoLots {
    private QuiverAmmoLots() {}

    static List<LotLocationRecord> all(List<LotLocationRecord> records, ItemId quiverItemId) {
        Objects.requireNonNull(records, "records");
        ValueLocation location = ValueLocation.quiver(quiverItemId);
        return records.stream()
                .filter(record -> record.quantity() > 0)
                .filter(record -> record.location().equals(location))
                .sorted(java.util.Comparator.comparing(record -> record.lotId().value()))
                .toList();
    }

    static Optional<LotLocationRecord> select(
            List<LotLocationRecord> records, ItemId quiverItemId, DefinitionId definitionId) {
        Objects.requireNonNull(definitionId, "definitionId");
        return all(records, quiverItemId).stream()
                .filter(record -> record.definitionId().equals(definitionId))
                .findFirst();
    }

    static long quantity(
            List<LotLocationRecord> records, ItemId quiverItemId, DefinitionId definitionId) {
        Objects.requireNonNull(definitionId, "definitionId");
        return sum(
                all(records, quiverItemId).stream()
                        .filter(record -> record.definitionId().equals(definitionId))
                        .toList());
    }

    static long usedCapacity(List<LotLocationRecord> records, ItemId quiverItemId) {
        return sum(all(records, quiverItemId));
    }

    private static long sum(List<LotLocationRecord> records) {
        long quantity = 0;
        for (LotLocationRecord record : records) {
            if (Long.MAX_VALUE - quantity < record.quantity()) {
                return Long.MAX_VALUE;
            }
            quantity += record.quantity();
        }
        return quantity;
    }
}
