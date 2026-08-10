package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.projection.ExpectedProjection;
import com.branz.mmorpg.items.projection.ObservedProjection;
import com.branz.mmorpg.persistence.transaction.LotLocationRecord;
import java.nio.file.Path;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalLotAuthorityIntegrationTest {
    private static final String CONTENT = "content.test.1";
    private static final DefinitionId LOT = DefinitionId.of("consumable.test.authority");

    @Test
    void exactSelectedSlotMatchesDatabaseLotTruth(
            @org.junit.jupiter.api.io.TempDir Path databaseDirectory) throws Exception {
        try (DatabaseRuntime database = DatabaseRuntime.start(settings(databaseDirectory))) {
            CharacterSessionService sessions = new CharacterSessionService(database);
            LoadedCharacterSession opened = success(sessions.open(UUID.randomUUID()));
            LoadedCharacterSession granted =
                    success(sessions.grantTestValue(opened, lotDefinition(), 3, 7, CONTENT));
            ExpectedProjection expected =
                    granted.snapshot().inventory().stream()
                            .filter(projection -> projection.slot() == 3)
                            .findFirst()
                            .orElseThrow();
            ObservedProjection exact = observed(expected, 3);

            Result<LotLocationRecord, PhysicalLotResolutionErrorCode> resolved =
                    PhysicalLotAuthority.resolve(granted.characterId(), 3, exact, granted.snapshot());

            assertTrue(resolved instanceof Result.Success<?, ?>);
            LotLocationRecord record =
                    ((Result.Success<LotLocationRecord, PhysicalLotResolutionErrorCode>) resolved)
                            .value();
            assertEquals(expected.valueId(), record.lotId().value());
            assertEquals(7, record.quantity());

            Result<LotLocationRecord, PhysicalLotResolutionErrorCode> wrongSlot =
                    PhysicalLotAuthority.resolve(
                            granted.characterId(), 2, observed(expected, 2), granted.snapshot());
            assertTrue(wrongSlot instanceof Result.Failure<?, ?>);
            assertEquals(
                    PhysicalLotResolutionErrorCode.PHYSICAL_LOT_PROJECTION_STALE,
                    ((Result.Failure<LotLocationRecord, PhysicalLotResolutionErrorCode>) wrongSlot)
                            .error());
            sessions.close(granted);
        }
    }

    private static ObservedProjection observed(ExpectedProjection expected, int slot) {
        return new ObservedProjection(
                slot,
                expected.valueId(),
                expected.definitionId(),
                expected.valueType(),
                expected.quantity(),
                expected.authorityVersion(),
                expected.displayRevision(),
                expected.contentVersion(),
                expected.testProvenance(),
                true);
    }

    private static ItemDefinition lotDefinition() {
        return new ItemDefinition(
                LOT, LOT, ItemClass.STACKABLE_LOT, OptionalInt.empty(), false);
    }

    private static DatabaseSettings settings(Path directory) {
        return new DatabaseSettings(
                DatabaseMode.EMBEDDED_LOCAL,
                "LOCAL",
                directory,
                "",
                "",
                "",
                4,
                Duration.ofSeconds(5),
                true,
                Duration.ofSeconds(30),
                Duration.ofSeconds(10));
    }

    private static LoadedCharacterSession success(
            Result<LoadedCharacterSession, CharacterSessionErrorCode> result) {
        if (result
                instanceof Result.Failure<LoadedCharacterSession, CharacterSessionErrorCode>
                        failure) {
            throw new AssertionError(failure.error().code() + ": " + failure.detail());
        }
        return ((Result.Success<LoadedCharacterSession, CharacterSessionErrorCode>) result).value();
    }
}
