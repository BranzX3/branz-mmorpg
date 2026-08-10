package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.WeaponCombatProfile;
import com.branz.mmorpg.persistence.transaction.ValueLocation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WeaponDurabilityServiceIntegrationTest {
    private static final String CONTENT_VERSION = "content.test.1";
    private static final DefinitionId SWORD_ID = DefinitionId.of("weapon.test_sword");
    private static final DefinitionId MOVE_ID = DefinitionId.of("move.test_sword.primary_1");

    @Test
    void successfulPhysicalWeaponWearIsAtomicAndSurvivesReconnect(
            @org.junit.jupiter.api.io.TempDir Path databaseDirectory) throws Exception {
        DatabaseSettings settings = settings(databaseDirectory);
        UUID playerId = UUID.randomUUID();
        ItemId swordItemId;

        try (DatabaseRuntime database = DatabaseRuntime.start(settings)) {
            CharacterSessionService sessions = new CharacterSessionService(database);
            WeaponDurabilityService durability = new WeaponDurabilityService(database, sessions);
            LoadedCharacterSession opened = success(sessions.open(playerId));
            LoadedCharacterSession granted =
                    success(sessions.grantTestValue(opened, swordDefinition(), 3, CONTENT_VERSION));
            swordItemId =
                    granted.snapshot().itemRecords().stream()
                            .filter(record -> record.definitionId().equals(SWORD_ID))
                            .findFirst()
                            .orElseThrow()
                            .itemId();

            UUID firstOperation = UUID.randomUUID();
            LoadedCharacterSession first =
                    success(
                            durability.commitSuccessfulAttack(
                                    granted,
                                    swordItemId,
                                    SWORD_ID,
                                    3,
                                    1,
                                    MOVE_ID,
                                    firstOperation,
                                    CONTENT_VERSION));
            assertEquals(
                    2, durability.authoritativeState(first, swordItemId, SWORD_ID, 3).current());

            LoadedCharacterSession replay =
                    success(
                            durability.commitSuccessfulAttack(
                                    granted,
                                    swordItemId,
                                    SWORD_ID,
                                    3,
                                    1,
                                    MOVE_ID,
                                    firstOperation,
                                    CONTENT_VERSION));
            assertEquals(
                    2, durability.authoritativeState(replay, swordItemId, SWORD_ID, 3).current());

            LoadedCharacterSession second =
                    success(
                            durability.commitSuccessfulAttack(
                                    first,
                                    swordItemId,
                                    SWORD_ID,
                                    3,
                                    1,
                                    MOVE_ID,
                                    UUID.randomUUID(),
                                    CONTENT_VERSION));
            LoadedCharacterSession third =
                    success(
                            durability.commitSuccessfulAttack(
                                    second,
                                    swordItemId,
                                    SWORD_ID,
                                    3,
                                    1,
                                    MOVE_ID,
                                    UUID.randomUUID(),
                                    CONTENT_VERSION));
            assertTrue(durability.authoritativeState(third, swordItemId, SWORD_ID, 3).broken());
            assertEquals(
                    ValueLocation.inventory("slot:3"),
                    third.snapshot().itemRecords().stream()
                            .filter(record -> record.itemId().equals(swordItemId))
                            .findFirst()
                            .orElseThrow()
                            .location());

            Result<LoadedCharacterSession, CharacterSessionErrorCode> exhausted =
                    durability.commitSuccessfulAttack(
                            third,
                            swordItemId,
                            SWORD_ID,
                            3,
                            1,
                            MOVE_ID,
                            UUID.randomUUID(),
                            CONTENT_VERSION);
            assertTrue(exhausted instanceof Result.Failure<?, ?>);
            sessions.close(third);
        }

        try (DatabaseRuntime restarted = DatabaseRuntime.start(settings)) {
            CharacterSessionService sessions = new CharacterSessionService(restarted);
            WeaponDurabilityService durability = new WeaponDurabilityService(restarted, sessions);
            LoadedCharacterSession restored = success(sessions.open(playerId));

            assertEquals(
                    ValueLocation.inventory("slot:3"),
                    restored.snapshot().itemRecords().stream()
                            .filter(record -> record.itemId().equals(swordItemId))
                            .findFirst()
                            .orElseThrow()
                            .location());
            assertTrue(durability.authoritativeState(restored, swordItemId, SWORD_ID, 3).broken());
            sessions.close(restored);
        }
    }

    @Test
    void payloadCodecPreservesProvenanceAndAdvancesDisplayRevision() {
        String original = "{\"displayRevision\":7,\"testProvenance\":\"dev:test\"}";

        String encoded = WeaponPayloadCodec.encode(original, new WeaponDurability(4, 5));

        assertEquals(4, WeaponPayloadCodec.decode(encoded, 5).current());
        assertTrue(encoded.contains("\"displayRevision\":8"));
        assertTrue(encoded.contains("\"testProvenance\":\"dev:test\""));
    }

    private static ItemDefinition swordDefinition() {
        return new ItemDefinition(
                SWORD_ID,
                DefinitionId.of("asset.weapon.test_sword"),
                ItemClass.UNIQUE_DURABLE,
                OptionalInt.of(3),
                false,
                Optional.of(new WeaponCombatProfile("SWORD", 100)));
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
