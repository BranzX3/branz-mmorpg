package com.branz.mmorpg.combat.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.definition.DefinitionRegistry;
import com.branz.mmorpg.content.manifest.ContentManifest;
import com.branz.mmorpg.content.reference.ReferenceIndex;
import com.branz.mmorpg.content.schema.DefinitionType;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AilmentDefinitionEngineTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void compilesCompleteAilmentRuntimeContract() throws Exception {
        Result<AilmentDefinitionEngine, AilmentDefinitionEngineErrorCode> result =
                AilmentDefinitionEngine.compile(snapshot(definition("status.burn", "BURN")));

        assertTrue(result.isSuccess());
        AilmentDefinition burn =
                ((Result.Success<AilmentDefinitionEngine, AilmentDefinitionEngineErrorCode>) result)
                        .value()
                        .find(AilmentType.BURN)
                        .orElseThrow();
        assertEquals(100, burn.buildupMaximum());
        assertEquals(AilmentReapplication.REFRESH, burn.reapplication());
        assertEquals(0.7, burn.pvpMultiplier());
        assertEquals("status.burn.flame", burn.visualCue());
    }

    @Test
    void rejectsIdentityMismatchBeforeRuntimeActivation() throws Exception {
        Result<AilmentDefinitionEngine, AilmentDefinitionEngineErrorCode> mismatch =
                AilmentDefinitionEngine.compile(snapshot(definition("status.poison", "BURN")));

        assertFalse(mismatch.isSuccess());
        assertEquals(
                AilmentDefinitionEngineErrorCode.AILMENT_DEFINITION_INVALID,
                ((Result.Failure<AilmentDefinitionEngine, AilmentDefinitionEngineErrorCode>)
                                mismatch)
                        .error());
    }

    private static ContentDefinition definition(String id, String type) throws Exception {
        String body =
                """
                {
                  "definition_id": "%s",
                  "schema_version": 1,
                  "ailment_type": "%s",
                  "buildup_max": 100,
                  "buildup_decay_delay_ticks": 40,
                  "buildup_decay_per_tick": 1,
                  "active_duration_ticks": 160,
                  "reapplication": "REFRESH",
                  "maximum_tier": 1,
                  "resistance_channel": "FIRE",
                  "cleanse_tags": ["WATER"],
                  "persistence": "CLEAR_ON_DEATH",
                  "profiles": {"pve_multiplier": 1, "pvp_multiplier": 0.7},
                  "presentation": {
                    "visual_cue": "status.burn.flame",
                    "audio_cue": "status.burn.ignite"
                  }
                }
                """
                        .formatted(id, type);
        return new ContentDefinition(
                DefinitionId.of(id),
                DefinitionType.STATUS,
                1,
                Path.of(id + ".json"),
                JSON.readTree(body),
                List.of());
    }

    private static ContentSnapshot snapshot(ContentDefinition... definitions) {
        return new ContentSnapshot(
                new ContentManifest(
                        "test-content",
                        1,
                        "1.x",
                        "26.2",
                        "pack",
                        "bundle",
                        "commit",
                        Map.of(),
                        Map.of("statuses", definitions.length)),
                DefinitionRegistry.of(List.of(definitions)),
                ReferenceIndex.of(List.of()));
    }
}
