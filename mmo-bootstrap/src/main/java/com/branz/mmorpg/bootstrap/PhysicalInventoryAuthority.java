package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.projection.ExpectedProjection;
import com.branz.mmorpg.items.projection.ObservedProjection;
import com.branz.mmorpg.items.projection.ProjectionValueType;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocationType;
import java.util.Objects;

final class PhysicalInventoryAuthority {
    private PhysicalInventoryAuthority() {}

    static Result<ItemLocationRecord, PhysicalItemResolutionErrorCode> resolveUniqueItem(
            CharacterId characterId,
            int selectedSlot,
            ObservedProjection observed,
            PersistentCharacterSnapshot snapshot) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(snapshot, "snapshot");
        if (selectedSlot < 0 || selectedSlot >= ChronicleService.HOTBAR_SLOT) {
            return Result.failure(
                    PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_SLOT_NOT_GAMEPLAY,
                    "Selected slot is not a gameplay hotbar slot 1-8.");
        }
        if (observed.slot() != selectedSlot || !observed.signatureValid()) {
            return Result.failure(
                    PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_PROJECTION_INVALID,
                    "Physical item projection is unsigned, malformed or from another slot.");
        }
        if (observed.valueType() != ProjectionValueType.UNIQUE_ITEM || observed.quantity() != 1) {
            return Result.failure(
                    PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_NOT_UNIQUE,
                    "Selected projection is not one durable unique item.");
        }
        ExpectedProjection expected =
                snapshot.inventory().stream()
                        .filter(projection -> projection.slot() == selectedSlot)
                        .findFirst()
                        .orElse(null);
        if (expected == null || !matches(expected, observed)) {
            return Result.failure(
                    PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_PROJECTION_STALE,
                    "Selected physical projection does not match current database inventory truth.");
        }
        ItemId itemId = new ItemId(observed.valueId());
        ItemLocationRecord record =
                snapshot.itemRecords().stream()
                        .filter(item -> item.itemId().equals(itemId))
                        .findFirst()
                        .orElse(null);
        if (record == null) {
            return Result.failure(
                    PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_RECORD_MISSING,
                    "Selected item UUID is absent from authoritative item records.");
        }
        if (record.ownerCharacterId().filter(characterId::equals).isEmpty()) {
            return Result.failure(
                    PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_OWNER_MISMATCH,
                    "Selected item is not owned by the active character.");
        }
        if (record.location().type() != ValueLocationType.CHARACTER_INVENTORY
                || record.location()
                        .reference()
                        .filter(("slot:" + selectedSlot)::equals)
                        .isEmpty()) {
            return Result.failure(
                    PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_LOCATION_MISMATCH,
                    "Selected item is not authoritative character inventory at the selected slot.");
        }
        if (!record.definitionId().equals(observed.definitionId())
                || record.version() != observed.authorityVersion()
                || !record.contentVersion().equals(observed.contentVersion())) {
            return Result.failure(
                    PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_PROJECTION_STALE,
                    "Selected projection identity/version no longer matches its item record.");
        }
        return Result.success(record);
    }

    private static boolean matches(ExpectedProjection expected, ObservedProjection observed) {
        return expected.slot() == observed.slot()
                && expected.valueId().equals(observed.valueId())
                && expected.definitionId().equals(observed.definitionId())
                && expected.valueType() == observed.valueType()
                && expected.quantity() == observed.quantity()
                && expected.authorityVersion() == observed.authorityVersion()
                && expected.displayRevision() == observed.displayRevision()
                && expected.contentVersion().equals(observed.contentVersion())
                && expected.testProvenance().equals(observed.testProvenance());
    }
}
