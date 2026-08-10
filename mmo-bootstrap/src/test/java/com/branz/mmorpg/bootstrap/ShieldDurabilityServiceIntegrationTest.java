package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.GuardCombatProfile;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ShieldProfile;
import com.branz.mmorpg.items.equipment.EquipmentSlot;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShieldDurabilityServiceIntegrationTest {
    private static final String CONTENT_VERSION = "content.test.1";
    private static final DefinitionId SHIELD_ID = DefinitionId.of("equipment.test_shield");

    @Test
    void physicalOffhandBlockedImpactWearIsAtomicIdempotentAndSurvivesRestart(
            @org.junit.jupiter.api.io.TempDir Path databaseDirectory) throws Exception {
        DatabaseSettings settings = settings(databaseDirectory);
        UUID playerId = UUID.randomUUID();
        ItemId shieldItemId;

        try (DatabaseRuntime database = DatabaseRuntime.start(settings)) {
            CharacterSessionService sessions = new CharacterSessionService(database);
            PhysicalOffHandItemMoveService offHand =
                    new PhysicalOffHandItemMoveService(database, sessions);
            ShieldDurabilityService durability = new ShieldDurabilityService(database, sessions);
            LoadedCharacterSession opened = success(sessions.open(playerId));
            LoadedCharacterSession granted =
                    success(
                            sessions.grantTestValue(
                                    opened, shieldDefinition(), 3, CONTENT_VERSION));
            shieldItemId =
                    granted.snapshot().itemRecords().stream()
                            .filter(record -> record.definitionId().equals(SHIELD_ID))
                            .findFirst()
                            .orElseThrow()
                            .itemId();
            LoadedCharacterSession equipped =
                    success(
                            offHand.swap(
                                    granted,
                                    3,
                                    Optional.of(shieldItemId),
                                    UUID.randomUUID(),
                                    CONTENT_VERSION));
            assertEquals(
                    shieldItemId,
                    equipped.snapshot().equipment().item(EquipmentSlot.OFF_HAND).orElseThrow());
            assertEquals(
                    ValueLocation.nativeEquipped("OFF_HAND"),
                    equipped.snapshot().itemRecords().stream()
                            .filter(record -> record.itemId().equals(shieldItemId))
                            .findFirst()
                            .orElseThrow()
                            .location());

            UUID firstImpact = UUID.randomUUID();
            LoadedCharacterSession first =
                    success(
                            durability.commitBlockedImpact(
                                    equipped,
                                    shieldItemId,
                                    SHIELD_ID,
                                    3,
                                    firstImpact,
                                    CONTENT_VERSION));
            assertEquals(
                    2, durability.authoritativeState(first, shieldItemId, SHIELD_ID, 3).current());

            LoadedCharacterSession replay =
                    success(
                            durability.commitBlockedImpact(
                                    equipped,
                                    shieldItemId,
                                    SHIELD_ID,
                                    3,
                                    firstImpact,
                                    CONTENT_VERSION));
            assertEquals(
                    2, durability.authoritativeState(replay, shieldItemId, SHIELD_ID, 3).current());

            LoadedCharacterSession second =
                    success(
                            durability.commitBlockedImpact(
                                    first,
                                    shieldItemId,
                                    SHIELD_ID,
                                    3,
                                    UUID.randomUUID(),
                                    CONTENT_VERSION));
            LoadedCharacterSession third =
                    success(
                            durability.commitBlockedImpact(
                                    second,
                                    shieldItemId,
                                    SHIELD_ID,
                                    3,
                                    UUID.randomUUID(),
                                    CONTENT_VERSION));
            assertTrue(durability.authoritativeState(third, shieldItemId, SHIELD_ID, 3).broken());

            Result<LoadedCharacterSession, CharacterSessionErrorCode> exhausted =
                    durability.commitBlockedImpact(
                            third, shieldItemId, SHIELD_ID, 3, UUID.randomUUID(), CONTENT_VERSION);
            assertTrue(exhausted instanceof Result.Failure<?, ?>);
            sessions.close(third);
        }

        try (DatabaseRuntime restarted = DatabaseRuntime.start(settings)) {
            CharacterSessionService sessions = new CharacterSessionService(restarted);
            ShieldDurabilityService durability = new ShieldDurabilityService(restarted, sessions);
            LoadedCharacterSession restored = success(sessions.open(playerId));

            assertEquals(
                    shieldItemId,
                    restored.snapshot().equipment().item(EquipmentSlot.OFF_HAND).orElseThrow());
            assertEquals(
                    ValueLocation.nativeEquipped("OFF_HAND"),
                    restored.snapshot().itemRecords().stream()
                            .filter(record -> record.itemId().equals(shieldItemId))
                            .findFirst()
                            .orElseThrow()
                            .location());
            assertTrue(
                    durability.authoritativeState(restored, shieldItemId, SHIELD_ID, 3).broken());
            sessions.close(restored);
        }
    }

    private static ItemDefinition shieldDefinition() {
        return new ItemDefinition(
                SHIELD_ID,
                DefinitionId.of("asset.equipment.test_shield"),
                ItemClass.UNIQUE_DURABLE,
                OptionalInt.of(3),
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(
                        new ShieldProfile(
                                new GuardCombatProfile(145, 0.9, 4, 130, 24, 24, 10, 22, 50))));
    }

    private static DatabaseSettings settings(Path databaseDirectory) {
        return new DatabaseSettings(
                DatabaseMode.EMBEDDED_LOCAL,
                "LOCAL",
                databaseDirectory,
                "",
                "",
                "",
                4,
                Duration.ofSeconds(5),
                true,
                Duration.ofMinutes(5),
                Duration.ofSeconds(10));
    }

    private static LoadedCharacterSession success(
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result) {
        assertTrue(result instanceof Result.Success<?, ?>);
        return ((Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>) result).value();
    }
}
