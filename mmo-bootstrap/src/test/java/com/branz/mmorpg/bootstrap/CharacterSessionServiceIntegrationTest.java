package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalInt;
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

            Result<LoadedCharacterSession, CharacterSessionErrorCode> conflict =
                    service.open(playerId);
            assertTrue(conflict instanceof Result.Failure<?, ?>);
            assertEquals(
                    CharacterSessionErrorCode.CHARACTER_LEASE_CONFLICT,
                    ((Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>) conflict)
                            .error());

            service.close(equipped);
            LoadedCharacterSession reconnected = success(service.open(playerId));
            assertEquals(1, reconnected.snapshot().inventory().size());
            assertEquals(
                    granted.snapshot().inventory().getFirst().valueId(),
                    reconnected.snapshot().inventory().getFirst().valueId());
            assertEquals(
                    bladeId,
                    reconnected.snapshot().equipment().item(EquipmentSlot.MAIN_HAND).orElseThrow());
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
