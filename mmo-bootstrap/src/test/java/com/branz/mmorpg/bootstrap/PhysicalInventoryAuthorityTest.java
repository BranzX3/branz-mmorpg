package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.TransactionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.projection.ObservedProjection;
import com.branz.mmorpg.items.projection.ProjectionValueType;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalInventoryAuthorityTest {
    private static final DefinitionId SWORD = DefinitionId.of("weapon.test.sword");
    private static final String CONTENT = "content.test.1";

    @Test
    void exactSignedProjectionResolvesAuthoritativeInventoryItem() {
        CharacterId owner = new CharacterId(UUID.randomUUID());
        ItemLocationRecord item = item(owner, 5, 7);
        PersistentCharacterSnapshot snapshot =
                PersistentCharacterSnapshotMapper.map(List.of(item), List.of());

        Result<ItemLocationRecord, PhysicalItemResolutionErrorCode> result =
                PhysicalInventoryAuthority.resolveUniqueItem(
                        owner, 5, observed(item, 5, 7, true), snapshot);

        assertTrue(result.isSuccess());
        assertEquals(
                item,
                ((Result.Success<ItemLocationRecord, PhysicalItemResolutionErrorCode>) result)
                        .value());
    }

    @Test
    void staleAuthorityVersionIsRejected() {
        CharacterId owner = new CharacterId(UUID.randomUUID());
        ItemLocationRecord item = item(owner, 3, 9);
        PersistentCharacterSnapshot snapshot =
                PersistentCharacterSnapshotMapper.map(List.of(item), List.of());

        Result<ItemLocationRecord, PhysicalItemResolutionErrorCode> result =
                PhysicalInventoryAuthority.resolveUniqueItem(
                        owner, 3, observed(item, 3, 8, true), snapshot);

        assertFailure(result, PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_PROJECTION_STALE);
    }

    @Test
    void projectionFromAnotherSlotIsRejected() {
        CharacterId owner = new CharacterId(UUID.randomUUID());
        ItemLocationRecord item = item(owner, 6, 2);
        PersistentCharacterSnapshot snapshot =
                PersistentCharacterSnapshotMapper.map(List.of(item), List.of());

        Result<ItemLocationRecord, PhysicalItemResolutionErrorCode> result =
                PhysicalInventoryAuthority.resolveUniqueItem(
                        owner, 5, observed(item, 6, 2, true), snapshot);

        assertFailure(result, PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_PROJECTION_INVALID);
    }

    @Test
    void invalidSignatureIsRejectedBeforeDatabaseIdentityIsTrusted() {
        CharacterId owner = new CharacterId(UUID.randomUUID());
        ItemLocationRecord item = item(owner, 1, 4);
        PersistentCharacterSnapshot snapshot =
                PersistentCharacterSnapshotMapper.map(List.of(item), List.of());

        Result<ItemLocationRecord, PhysicalItemResolutionErrorCode> result =
                PhysicalInventoryAuthority.resolveUniqueItem(
                        owner, 1, observed(item, 1, 4, false), snapshot);

        assertFailure(result, PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_PROJECTION_INVALID);
    }

    @Test
    void ownerMismatchIsRejectedEvenWhenProjectionFieldsMatch() {
        CharacterId actualOwner = new CharacterId(UUID.randomUUID());
        CharacterId activeCharacter = new CharacterId(UUID.randomUUID());
        ItemLocationRecord item = item(actualOwner, 2, 5);
        PersistentCharacterSnapshot snapshot =
                PersistentCharacterSnapshotMapper.map(List.of(item), List.of());

        Result<ItemLocationRecord, PhysicalItemResolutionErrorCode> result =
                PhysicalInventoryAuthority.resolveUniqueItem(
                        activeCharacter, 2, observed(item, 2, 5, true), snapshot);

        assertFailure(result, PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_OWNER_MISMATCH);
    }

    @Test
    void nativeEquippedLegacyMainHandCannotMasqueradeAsPhysicalHotbarItem() {
        CharacterId owner = new CharacterId(UUID.randomUUID());
        ItemLocationRecord item =
                new ItemLocationRecord(
                        new ItemId(UUID.randomUUID()),
                        SWORD,
                        Optional.of(owner),
                        ValueLocation.nativeEquipped("MAIN_HAND"),
                        "{\"displayRevision\":1}",
                        CONTENT,
                        3,
                        new TransactionId(UUID.randomUUID()),
                        Instant.EPOCH,
                        Instant.EPOCH);
        PersistentCharacterSnapshot snapshot =
                PersistentCharacterSnapshotMapper.map(List.of(item), List.of());
        ObservedProjection observed =
                new ObservedProjection(
                        0,
                        item.itemId().value(),
                        item.definitionId(),
                        ProjectionValueType.UNIQUE_ITEM,
                        1,
                        item.version(),
                        1,
                        item.contentVersion(),
                        Optional.empty(),
                        true);

        Result<ItemLocationRecord, PhysicalItemResolutionErrorCode> result =
                PhysicalInventoryAuthority.resolveUniqueItem(owner, 0, observed, snapshot);

        assertFailure(result, PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_PROJECTION_STALE);
    }

    @Test
    void chronicleSlotIsNeverPhysicalGameplayAuthority() {
        CharacterId owner = new CharacterId(UUID.randomUUID());
        ItemLocationRecord item = item(owner, 7, 1);
        PersistentCharacterSnapshot snapshot =
                PersistentCharacterSnapshotMapper.map(List.of(item), List.of());

        Result<ItemLocationRecord, PhysicalItemResolutionErrorCode> result =
                PhysicalInventoryAuthority.resolveUniqueItem(
                        owner,
                        ChronicleService.HOTBAR_SLOT,
                        observed(item, 7, 1, true),
                        snapshot);

        assertFailure(result, PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_SLOT_NOT_GAMEPLAY);
    }

    private static ItemLocationRecord item(CharacterId owner, int slot, long version) {
        return new ItemLocationRecord(
                new ItemId(UUID.randomUUID()),
                SWORD,
                Optional.of(owner),
                ValueLocation.inventory("slot:" + slot),
                "{\"displayRevision\":1}",
                CONTENT,
                version,
                new TransactionId(UUID.randomUUID()),
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static ObservedProjection observed(
            ItemLocationRecord item, int slot, long version, boolean signatureValid) {
        return new ObservedProjection(
                slot,
                item.itemId().value(),
                item.definitionId(),
                ProjectionValueType.UNIQUE_ITEM,
                1,
                version,
                1,
                item.contentVersion(),
                Optional.empty(),
                signatureValid);
    }

    private static void assertFailure(
            Result<ItemLocationRecord, PhysicalItemResolutionErrorCode> result,
            PhysicalItemResolutionErrorCode expected) {
        assertTrue(result instanceof Result.Failure<?, ?>);
        assertEquals(
                expected,
                ((Result.Failure<ItemLocationRecord, PhysicalItemResolutionErrorCode>) result)
                        .error());
    }
}
