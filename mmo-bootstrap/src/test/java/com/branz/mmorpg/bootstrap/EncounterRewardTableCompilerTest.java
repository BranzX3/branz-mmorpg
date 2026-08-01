package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.ErrorCode;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.content.snapshot.ContentSnapshotLoader;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.worldloop.reward.EncounterRewardTable;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EncounterRewardTableCompilerTest {
    @Test
    void exampleContentCompilesAuthoredTrainingBossRewardTable() {
        Path fixture = Path.of("..", "example-content", "milestone-1").toAbsolutePath().normalize();
        ContentSnapshot snapshot = success(new ContentSnapshotLoader().load(fixture));
        ItemEngine items = success(ItemEngine.compile(snapshot));

        Map<DefinitionId, EncounterRewardTable> tables =
                EncounterRewardTableCompiler.compile(snapshot, items);
        EncounterRewardTable table = tables.get(DefinitionId.of("encounter.boss.training_golem"));
        assertEquals(2, table.entries().size());
        assertEquals(0.20, table.lateJoinHpRatio());
        assertEquals(600, table.eligibilityProfile().maximumIdleTicks());
        assertEquals(4, table.entries().stream().mapToLong(entry -> entry.weight()).sum());
    }

    private static <T, E extends ErrorCode> T success(Result<T, E> result) {
        assertTrue(
                result.isSuccess(),
                () -> result instanceof Result.Failure<T, E> failure ? failure.detail() : "");
        return ((Result.Success<T, E>) result).value();
    }
}
