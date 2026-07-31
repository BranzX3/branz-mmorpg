package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.identity.LotId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.crossbow.CrossbowPersistentState;
import com.branz.mmorpg.items.definition.AmmoFamily;
import com.branz.mmorpg.items.definition.AmmoProfile;
import com.branz.mmorpg.items.definition.CatalystProfile;
import com.branz.mmorpg.items.definition.CrossbowWeaponProfile;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.QuiverProfile;
import com.branz.mmorpg.items.definition.WeaponCombatProfile;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.items.quiver.QuiverPreparation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CharacterSessionServiceIntegrationTest {
    @Test
    void catalystWearCommitsExactlyOnceAndSurvivesReconnectAndRestart(
            @org.junit.jupiter.api.io.TempDir Path databaseDirectory) throws Exception {
        DatabaseSettings settings =
                new DatabaseSettings(
                        DatabaseMode.EMBEDDED_LOCAL,
                        "LOCAL",
                        databaseDirectory,
                        "",
                        "",
                        "",
                        4,
                        Duration.ofSeconds(5),
                        true,
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(10));
        UUID playerId = UUID.randomUUID();
        ItemId staffId;
        try (DatabaseRuntime database = DatabaseRuntime.start(settings)) {
            CharacterSessionService service = new CharacterSessionService(database);
            LoadedCharacterSession session = success(service.open(playerId));
            DefinitionId staffDefinitionId = DefinitionId.of("weapon.test.staff");
            ItemDefinition staff =
                    new ItemDefinition(
                            staffDefinitionId,
                            staffDefinitionId,
                            ItemClass.UNIQUE_DURABLE,
                            OptionalInt.of(100),
                            false,
                            Optional.of(new WeaponCombatProfile("STAFF", 95)),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.of(new CatalystProfile(Set.of("STAFF", "EMBER"), 0.85, 1)));
            session = success(service.grantTestValue(session, staff, 0, "content.test.1"));
            staffId =
                    new ItemId(
                            session.snapshot().inventory().stream()
                                    .filter(value -> value.definitionId().equals(staffDefinitionId))
                                    .findFirst()
                                    .orElseThrow()
                                    .valueId());
            session =
                    success(
                            service.commitEquipment(
                                    session,
                                    session.snapshot()
                                            .equipment()
                                            .with(EquipmentSlot.MAIN_HAND, Optional.of(staffId)),
                                    "content.test.1"));
            LoadedCharacterSession beforeCommit = session;
            UUID operationId = UUID.randomUUID();
            LoadedCharacterSession committed =
                    success(
                            service.commitCatalystUse(
                                    beforeCommit,
                                    staffId,
                                    staffDefinitionId,
                                    100,
                                    1,
                                    DefinitionId.of("spell.ember.fire_lance"),
                                    operationId,
                                    "content.test.1"));
            assertEquals(new CatalystDurability(99, 100), catalystState(committed, staffId, 100));

            LoadedCharacterSession replayed =
                    success(
                            service.commitCatalystUse(
                                    beforeCommit,
                                    staffId,
                                    staffDefinitionId,
                                    100,
                                    1,
                                    DefinitionId.of("spell.ember.fire_lance"),
                                    operationId,
                                    "content.test.1"));
            assertEquals(new CatalystDurability(99, 100), catalystState(replayed, staffId, 100));

            Result<LoadedCharacterSession, CharacterSessionErrorCode> stale =
                    service.commitCatalystUse(
                            beforeCommit,
                            staffId,
                            staffDefinitionId,
                            100,
                            1,
                            DefinitionId.of("spell.ember.fire_lance"),
                            UUID.randomUUID(),
                            "content.test.1");
            assertTrue(stale instanceof Result.Failure<?, ?>);

            service.close(replayed);
            session = success(service.open(playerId));
            assertEquals(new CatalystDurability(99, 100), catalystState(session, staffId, 100));
            service.close(session);
        }
        try (DatabaseRuntime restarted = DatabaseRuntime.start(settings)) {
            CharacterSessionService service = new CharacterSessionService(restarted);
            LoadedCharacterSession session = success(service.open(playerId));
            assertEquals(new CatalystDurability(99, 100), catalystState(session, staffId, 100));
            service.close(session);
        }
    }

    @Test
    void crossbowCheckpointsAndBoundBoltSurviveReconnectAndRestart(
            @org.junit.jupiter.api.io.TempDir Path databaseDirectory) throws Exception {
        DatabaseSettings settings =
                new DatabaseSettings(
                        DatabaseMode.EMBEDDED_LOCAL,
                        "LOCAL",
                        databaseDirectory,
                        "",
                        "",
                        "",
                        4,
                        Duration.ofSeconds(5),
                        true,
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(10));
        UUID playerId = UUID.randomUUID();
        ItemId crossbowId;
        ItemId quiverId;
        DefinitionId boltId = DefinitionId.of("ammo.test.bolt");
        try (DatabaseRuntime database = DatabaseRuntime.start(settings)) {
            CharacterSessionService service = new CharacterSessionService(database);
            LoadedCharacterSession session = success(service.open(playerId));
            ItemDefinition crossbow =
                    new ItemDefinition(
                            DefinitionId.of("weapon.test.crossbow"),
                            DefinitionId.of("weapon.test.crossbow"),
                            ItemClass.UNIQUE_DURABLE,
                            OptionalInt.of(120),
                            false,
                            Optional.of(
                                    new WeaponCombatProfile(
                                            "CROSSBOW",
                                            110,
                                            Optional.empty(),
                                            Optional.of(new CrossbowWeaponProfile(12, 8)))));
            ItemDefinition boltQuiver =
                    new ItemDefinition(
                            DefinitionId.of("equipment.test.bolt_quiver"),
                            DefinitionId.of("equipment.test.bolt_quiver"),
                            ItemClass.UNIQUE_DURABLE,
                            OptionalInt.empty(),
                            false,
                            Optional.empty(),
                            Optional.empty(),
                            Optional.of(new QuiverProfile(64, Set.of(AmmoFamily.BOLT), 4, 6)));
            ItemDefinition bolt =
                    new ItemDefinition(
                            boltId,
                            boltId,
                            ItemClass.STACKABLE_LOT,
                            OptionalInt.empty(),
                            false,
                            Optional.empty(),
                            Optional.of(new AmmoProfile(AmmoFamily.BOLT)),
                            Optional.empty());

            session = success(service.grantTestValue(session, crossbow, 0, "content.test.1"));
            crossbowId =
                    new ItemId(
                            session.snapshot().inventory().stream()
                                    .filter(value -> value.definitionId().equals(crossbow.id()))
                                    .findFirst()
                                    .orElseThrow()
                                    .valueId());
            session =
                    success(
                            service.commitEquipment(
                                    session,
                                    session.snapshot()
                                            .equipment()
                                            .with(EquipmentSlot.MAIN_HAND, Optional.of(crossbowId)),
                                    "content.test.1"));
            session = success(service.grantTestValue(session, boltQuiver, 1, "content.test.1"));
            quiverId =
                    new ItemId(
                            session.snapshot().inventory().stream()
                                    .filter(value -> value.definitionId().equals(boltQuiver.id()))
                                    .findFirst()
                                    .orElseThrow()
                                    .valueId());
            session =
                    success(
                            service.commitEquipment(
                                    session,
                                    session.snapshot()
                                            .equipment()
                                            .with(EquipmentSlot.QUIVER, Optional.of(quiverId)),
                                    "content.test.1"));
            session = success(service.grantTestValue(session, bolt, 2, 10, "content.test.1"));
            LotId boltLot =
                    new LotId(
                            session.snapshot().inventory().stream()
                                    .filter(value -> value.definitionId().equals(bolt.id()))
                                    .findFirst()
                                    .orElseThrow()
                                    .valueId());
            session =
                    success(
                            service.transferQuiverAmmo(
                                    session,
                                    boltLot,
                                    10,
                                    true,
                                    64,
                                    UUID.randomUUID(),
                                    "content.test.1"));
            session =
                    success(
                            service.updateQuiverPreparation(
                                    session,
                                    QuiverPreparation.empty().toggle(boltId, 4),
                                    UUID.randomUUID(),
                                    "content.test.1"));

            assertEquals(CrossbowPersistentState.unloaded(), crossbowState(session, crossbowId));
            session =
                    success(
                            service.bindCrossbowBolt(
                                    session,
                                    crossbowId,
                                    boltId,
                                    UUID.randomUUID(),
                                    "content.test.1"));
            assertEquals(
                    CrossbowPersistentState.boltPlaced(boltId), crossbowState(session, crossbowId));
            assertEquals(
                    9, QuiverAmmoLots.quantity(session.snapshot().lotRecords(), quiverId, boltId));

            service.close(session);
            session = success(service.open(playerId));
            assertEquals(
                    CrossbowPersistentState.boltPlaced(boltId), crossbowState(session, crossbowId));
            session =
                    success(
                            service.completeCrossbowLoad(
                                    session,
                                    crossbowId,
                                    boltId,
                                    UUID.randomUUID(),
                                    "content.test.1"));
            assertEquals(
                    CrossbowPersistentState.loaded(boltId), crossbowState(session, crossbowId));

            service.close(session);
            session = success(service.open(playerId));
            assertEquals(
                    CrossbowPersistentState.loaded(boltId), crossbowState(session, crossbowId));
            session =
                    success(
                            service.fireCrossbow(
                                    session,
                                    crossbowId,
                                    boltId,
                                    UUID.randomUUID(),
                                    "content.test.1"));
            assertEquals(CrossbowPersistentState.unloaded(), crossbowState(session, crossbowId));
            assertEquals(
                    9, QuiverAmmoLots.quantity(session.snapshot().lotRecords(), quiverId, boltId));
            service.close(session);
        }
        try (DatabaseRuntime restarted = DatabaseRuntime.start(settings)) {
            CharacterSessionService service = new CharacterSessionService(restarted);
            LoadedCharacterSession session = success(service.open(playerId));
            assertEquals(CrossbowPersistentState.unloaded(), crossbowState(session, crossbowId));
            assertEquals(
                    9, QuiverAmmoLots.quantity(session.snapshot().lotRecords(), quiverId, boltId));
            service.close(session);
        }
    }

    @Test
    void persistedDevGrantSurvivesCleanLeaseReleaseAndReconnect(
            @org.junit.jupiter.api.io.TempDir Path databaseDirectory) throws Exception {
        DatabaseSettings settings =
                new DatabaseSettings(
                        DatabaseMode.EMBEDDED_LOCAL,
                        "LOCAL",
                        databaseDirectory,
                        "",
                        "",
                        "",
                        4,
                        Duration.ofSeconds(5),
                        true,
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(10));
        UUID playerId = UUID.randomUUID();
        try (DatabaseRuntime database = DatabaseRuntime.start(settings)) {
            CharacterSessionService service = new CharacterSessionService(database);
            LoadedCharacterSession first = success(service.open(playerId));
            assertTrue(first.snapshot().inventory().isEmpty());

            ItemDefinition ore =
                    new ItemDefinition(
                            DefinitionId.of("material.test.ore"),
                            DefinitionId.of("material.test.ore"),
                            ItemClass.STACKABLE_LOT,
                            OptionalInt.empty(),
                            false);
            LoadedCharacterSession granted =
                    success(service.grantTestValue(first, ore, 2, "content.test.1"));
            assertEquals(1, granted.snapshot().inventory().size());
            assertEquals(2, granted.snapshot().inventory().getFirst().slot());
            assertTrue(granted.snapshot().inventory().getFirst().testProvenance().isPresent());

            ItemDefinition blade =
                    new ItemDefinition(
                            DefinitionId.of("weapon.test.blade"),
                            DefinitionId.of("weapon.test.blade"),
                            ItemClass.UNIQUE_DURABLE,
                            OptionalInt.of(100),
                            false);
            LoadedCharacterSession withBlade =
                    success(service.grantTestValue(granted, blade, 3, "content.test.1"));
            ItemId bladeId =
                    new ItemId(
                            withBlade.snapshot().inventory().stream()
                                    .filter(
                                            projection ->
                                                    projection.definitionId().equals(blade.id()))
                                    .findFirst()
                                    .orElseThrow()
                                    .valueId());
            LoadedCharacterSession equipped =
                    success(
                            service.commitEquipment(
                                    withBlade,
                                    withBlade
                                            .snapshot()
                                            .equipment()
                                            .with(
                                                    EquipmentSlot.MAIN_HAND,
                                                    java.util.Optional.of(bladeId)),
                                    "content.test.1"));
            assertEquals(
                    bladeId,
                    equipped.snapshot().equipment().item(EquipmentSlot.MAIN_HAND).orElseThrow());
            assertTrue(
                    equipped.snapshot().inventory().stream()
                            .noneMatch(projection -> projection.valueId().equals(bladeId.value())));

            ItemDefinition quiver =
                    new ItemDefinition(
                            DefinitionId.of("equipment.test.quiver"),
                            DefinitionId.of("equipment.test.quiver"),
                            ItemClass.UNIQUE_DURABLE,
                            OptionalInt.empty(),
                            false,
                            Optional.empty(),
                            Optional.empty(),
                            Optional.of(new QuiverProfile(96, Set.of(AmmoFamily.ARROW), 4, 6)));
            LoadedCharacterSession withQuiver =
                    success(service.grantTestValue(equipped, quiver, 4, "content.test.1"));
            ItemId quiverId =
                    new ItemId(
                            withQuiver.snapshot().inventory().stream()
                                    .filter(
                                            projection ->
                                                    projection.definitionId().equals(quiver.id()))
                                    .findFirst()
                                    .orElseThrow()
                                    .valueId());
            LoadedCharacterSession quiverEquipped =
                    success(
                            service.commitEquipment(
                                    withQuiver,
                                    withQuiver
                                            .snapshot()
                                            .equipment()
                                            .with(EquipmentSlot.QUIVER, Optional.of(quiverId)),
                                    "content.test.1"));
            ItemDefinition arrow =
                    new ItemDefinition(
                            DefinitionId.of("ammo.test.arrow"),
                            DefinitionId.of("ammo.test.arrow"),
                            ItemClass.STACKABLE_LOT,
                            OptionalInt.empty(),
                            false,
                            Optional.empty(),
                            Optional.of(new AmmoProfile(AmmoFamily.ARROW)),
                            Optional.empty());
            LoadedCharacterSession withArrow =
                    success(service.grantTestValue(quiverEquipped, arrow, 5, 64, "content.test.1"));
            LotId arrowLot =
                    new LotId(
                            withArrow.snapshot().inventory().stream()
                                    .filter(
                                            projection ->
                                                    projection.definitionId().equals(arrow.id()))
                                    .findFirst()
                                    .orElseThrow()
                                    .valueId());
            LoadedCharacterSession arrowStored =
                    success(
                            service.transferQuiverAmmo(
                                    withArrow,
                                    arrowLot,
                                    64,
                                    true,
                                    96,
                                    UUID.randomUUID(),
                                    "content.test.1"));
            assertEquals(
                    arrowLot,
                    QuiverAmmoLots.select(arrowStored.snapshot().lotRecords(), quiverId, arrow.id())
                            .orElseThrow()
                            .lotId());
            ItemDefinition bodkin =
                    new ItemDefinition(
                            DefinitionId.of("ammo.test.bodkin"),
                            DefinitionId.of("ammo.test.bodkin"),
                            ItemClass.STACKABLE_LOT,
                            OptionalInt.empty(),
                            false,
                            Optional.empty(),
                            Optional.of(new AmmoProfile(AmmoFamily.ARROW)),
                            Optional.empty());
            LoadedCharacterSession withBodkin =
                    success(service.grantTestValue(arrowStored, bodkin, 6, 64, "content.test.1"));
            LotId bodkinLot =
                    new LotId(
                            withBodkin.snapshot().inventory().stream()
                                    .filter(
                                            projection ->
                                                    projection.definitionId().equals(bodkin.id()))
                                    .findFirst()
                                    .orElseThrow()
                                    .valueId());
            LoadedCharacterSession ammoStored =
                    success(
                            service.transferQuiverAmmo(
                                    withBodkin,
                                    bodkinLot,
                                    32,
                                    true,
                                    96,
                                    UUID.randomUUID(),
                                    "content.test.1"));
            assertEquals(
                    32,
                    ammoStored.snapshot().inventory().stream()
                            .filter(projection -> projection.definitionId().equals(bodkin.id()))
                            .findFirst()
                            .orElseThrow()
                            .quantity());
            QuiverPreparation preparation =
                    QuiverPreparation.empty()
                            .toggle(DefinitionId.of("ammo.test.arrow"), 4)
                            .toggle(DefinitionId.of("ammo.test.bodkin"), 4)
                            .cycle(1);
            LoadedCharacterSession prepared =
                    success(
                            service.updateQuiverPreparation(
                                    ammoStored, preparation, UUID.randomUUID(), "content.test.1"));
            assertEquals(preparation, prepared.snapshot().quiverPreparation());
            assertEquals(
                    96, QuiverAmmoLots.usedCapacity(prepared.snapshot().lotRecords(), quiverId));
            LoadedCharacterSession consumed =
                    success(
                            service.consumeAmmo(
                                    prepared,
                                    DefinitionId.of("ammo.test.bodkin"),
                                    UUID.randomUUID(),
                                    "content.test.1"));
            assertEquals(
                    95, QuiverAmmoLots.usedCapacity(consumed.snapshot().lotRecords(), quiverId));
            assertEquals(
                    64,
                    QuiverAmmoLots.quantity(
                            consumed.snapshot().lotRecords(), quiverId, arrow.id()));

            Result<LoadedCharacterSession, CharacterSessionErrorCode> conflict =
                    service.open(playerId);
            assertTrue(conflict instanceof Result.Failure<?, ?>);
            assertEquals(
                    CharacterSessionErrorCode.CHARACTER_LEASE_CONFLICT,
                    ((Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>) conflict)
                            .error());

            service.close(consumed);
            LoadedCharacterSession reconnected = success(service.open(playerId));
            assertEquals(2, reconnected.snapshot().inventory().size());
            assertEquals(
                    granted.snapshot().inventory().getFirst().valueId(),
                    reconnected.snapshot().inventory().getFirst().valueId());
            assertEquals(
                    bladeId,
                    reconnected.snapshot().equipment().item(EquipmentSlot.MAIN_HAND).orElseThrow());
            assertEquals(
                    quiverId,
                    reconnected.snapshot().equipment().item(EquipmentSlot.QUIVER).orElseThrow());
            assertEquals(preparation, reconnected.snapshot().quiverPreparation());
            assertEquals(
                    95, QuiverAmmoLots.usedCapacity(reconnected.snapshot().lotRecords(), quiverId));
            service.close(reconnected);
        }
        try (DatabaseRuntime restarted = DatabaseRuntime.start(settings)) {
            CharacterSessionService service = new CharacterSessionService(restarted);
            LoadedCharacterSession afterServerRestart = success(service.open(playerId));
            assertEquals(2, afterServerRestart.snapshot().inventory().size());
            assertTrue(
                    afterServerRestart
                            .snapshot()
                            .equipment()
                            .item(EquipmentSlot.MAIN_HAND)
                            .isPresent());
            assertEquals(
                    DefinitionId.of("ammo.test.bodkin"),
                    afterServerRestart.snapshot().quiverPreparation().selectedAmmo().orElseThrow());
            ItemId restartedQuiverId =
                    afterServerRestart
                            .snapshot()
                            .equipment()
                            .item(EquipmentSlot.QUIVER)
                            .orElseThrow();
            assertEquals(
                    95,
                    QuiverAmmoLots.usedCapacity(
                            afterServerRestart.snapshot().lotRecords(), restartedQuiverId));
            service.close(afterServerRestart);
        }
    }

    private static LoadedCharacterSession success(
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result) {
        assertTrue(
                result.isSuccess(),
                () -> {
                    if (result
                            instanceof
                            Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>
                                    failure) {
                        return failure.error() + ": " + failure.detail();
                    }
                    return "";
                });
        return ((Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>) result).value();
    }

    private static CrossbowPersistentState crossbowState(
            LoadedCharacterSession session, ItemId crossbowId) {
        return CrossbowPayloadCodec.decode(
                session.snapshot().itemRecords().stream()
                        .filter(item -> item.itemId().equals(crossbowId))
                        .findFirst()
                        .orElseThrow()
                        .payloadJson());
    }

    private static CatalystDurability catalystState(
            LoadedCharacterSession session, ItemId staffId, int baseMaximum) {
        return CatalystPayloadCodec.decode(
                session.snapshot().itemRecords().stream()
                        .filter(item -> item.itemId().equals(staffId))
                        .findFirst()
                        .orElseThrow()
                        .payloadJson(),
                baseMaximum);
    }
}
