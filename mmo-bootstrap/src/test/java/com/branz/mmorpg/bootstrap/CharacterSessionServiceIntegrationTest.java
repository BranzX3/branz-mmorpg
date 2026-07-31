package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.AmmoFamily;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.QuiverProfile;
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
            QuiverPreparation preparation =
                    QuiverPreparation.empty()
                            .toggle(DefinitionId.of("ammo.test.arrow"), 4)
                            .toggle(DefinitionId.of("ammo.test.bodkin"), 4)
                            .cycle(1);
            LoadedCharacterSession prepared =
                    success(
                            service.updateQuiverPreparation(
                                    quiverEquipped,
                                    preparation,
                                    UUID.randomUUID(),
                                    "content.test.1"));
            assertEquals(preparation, prepared.snapshot().quiverPreparation());

            Result<LoadedCharacterSession, CharacterSessionErrorCode> conflict =
                    service.open(playerId);
            assertTrue(conflict instanceof Result.Failure<?, ?>);
            assertEquals(
                    CharacterSessionErrorCode.CHARACTER_LEASE_CONFLICT,
                    ((Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>) conflict)
                            .error());

            service.close(prepared);
            LoadedCharacterSession reconnected = success(service.open(playerId));
            assertEquals(1, reconnected.snapshot().inventory().size());
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
            service.close(reconnected);
        }
        try (DatabaseRuntime restarted = DatabaseRuntime.start(settings)) {
            CharacterSessionService service = new CharacterSessionService(restarted);
            LoadedCharacterSession afterServerRestart = success(service.open(playerId));
            assertEquals(1, afterServerRestart.snapshot().inventory().size());
            assertTrue(
                    afterServerRestart
                            .snapshot()
                            .equipment()
                            .item(EquipmentSlot.MAIN_HAND)
                            .isPresent());
            assertEquals(
                    DefinitionId.of("ammo.test.bodkin"),
                    afterServerRestart.snapshot().quiverPreparation().selectedAmmo().orElseThrow());
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
}
