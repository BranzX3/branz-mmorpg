package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.projection.ExpectedProjection;
import com.branz.mmorpg.items.projection.ObservedProjection;
import com.branz.mmorpg.items.projection.ProjectionValueType;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocationType;
import java.util.Objects;

final class PhysicalLotAuthority {
    private PhysicalLotAuthority() {}

    static Result<LotLocationRecord, PhysicalLotResolutionErrorCode> resolve(
            CharacterId characterId,
            int selectedSlot,
            ObservedProjection observed,
            PersistentCharacterSnapshot snapshot) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(snapshot, "snapshot");
        if (selectedSlot < 0 || selectedSlot >= ChronicleService.HOTBAR_SLOT) {
            return Result.failure(
                    PhysicalLotResolutionErrorCode.PHYSICAL_LOT_SLOT_NOT_GAMEPLAY,
                    "Selected slot is not a gameplay hotbar slot 1-8.");
        }
        if (observed.slot() != selectedSlot || !observed.signatureValid()) {
            return Result.failure(
                    PhysicalLotResolutionErrorCode.PHYSICAL_LOT_PROJECTION_INVALID,
                    "Physical lot projection is unsigned, malformed or from another slot.");
        }
        if (observed.valueType() != ProjectionValueType.STACKABLE_LOT || observed.quantity() < 1) {
            return Result.failure(
                    PhysicalLotResolutionErrorCode.PHYSICAL_LOT_NOT_STACKABLE,
                    "Selected projection is not one positive stackable lot.");
        }
        ExpectedProjection expected =
                snapshot.inventory().stream()
                        .filter(projection -> projection.slot() == selectedSlot)
                        .findFirst()
                        .orElse(null);
        if (expected == null || !matches(expected, observed)) {
            return Result.failure(
                    PhysicalLotResolutionErrorCode.PHYSICAL_LOT_PROJECTION_STALE,
                    "Selected physical lot does not match current database inventory truth.");
        }
        LotId lotId = new LotId(observed.valueId());
        LotLocationRecord record =
                snapshot.lotRecords().stream()
                        .filter(lot -> lot.lotId().equals(lotId))
                        .findFirst()
                        .orElse(null);
        if (record == null) {
            return Result.failure(
                    PhysicalLotResolutionErrorCode.PHYSICAL_LOT_RECORD_MISSING,
                    "Selected lot UUID is absent from authoritative lot records.");
        }
        if (record.ownerCharacterId().filter(characterId::equals).isEmpty()) {
            return Result.failure(
                    PhysicalLotResolutionErrorCode.PHYSICAL_LOT_OWNER_MISMATCH,
                    "Selected lot is not owned by the active character.");
        }
        if (record.location().type() != ValueLocationType.CHARACTER_INVENTORY
                || record.location()
                        .reference()
                        .filter(("slot:" + selectedSlot)::equals)
                        .isEmpty()) {
            return Result.failure(
                    PhysicalLotResolutionErrorCode.PHYSICAL_LOT_LOCATION_MISMATCH,
                    "Selected lot is not authoritative character inventory at the selected slot.");
        }
        if (!record.definitionId().equals(observed.definitionId())
                || record.quantity() != observed.quantity()
                || record.version() != observed.authorityVersion()
                || !record.contentVersion().equals(observed.contentVersion())) {
            return Result.failure(
                    PhysicalLotResolutionErrorCode.PHYSICAL_LOT_PROJECTION_STALE,
                    "Selected projection identity/version/quantity no longer matches its lot record.");
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
