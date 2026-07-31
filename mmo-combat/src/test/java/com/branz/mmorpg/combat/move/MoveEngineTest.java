package com.branz.mmorpg.combat.move;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class MoveEngineTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void compilesCompleteMoveContractFromContentSnapshot() throws Exception {
        Result<MoveEngine, MoveEngineErrorCode> result = MoveEngine.compile(snapshot(validBody()));

        assertTrue(result.isSuccess());
        MoveDefinition move =
                ((Result.Success<MoveEngine, MoveEngineErrorCode>) result)
                        .value()
                        .find(DefinitionId.of("move.test.training_slash"))
                        .orElseThrow();
        assertEquals(14, move.phases().totalTicks());
        assertEquals(3, move.commitTick());
        assertEquals(12, move.costs().stamina());
        assertEquals(4, move.hitboxes().getFirst().tick());
        assertEquals(0.65, move.profiles().pvpMultiplier());
    }

    @Test
    void rejectsCommitOutsideTimelineWithoutPartialRegistry() throws Exception {
        String invalid = validBody().replace("\"commit_tick\": 3", "\"commit_tick\": 14");

        Result<MoveEngine, MoveEngineErrorCode> result = MoveEngine.compile(snapshot(invalid));

        assertEquals(
                MoveEngineErrorCode.MOVE_TIMELINE_INVALID,
                ((Result.Failure<MoveEngine, MoveEngineErrorCode>) result).error());
    }

    @Test
    void rejectsHitboxOutsideActivePhase() throws Exception {
        String invalid = validBody().replace("\"tick\": 4", "\"tick\": 1");

        Result<MoveEngine, MoveEngineErrorCode> result = MoveEngine.compile(snapshot(invalid));

        assertEquals(
                MoveEngineErrorCode.MOVE_HITBOX_INVALID,
                ((Result.Failure<MoveEngine, MoveEngineErrorCode>) result).error());
    }

    static ContentSnapshot snapshot(String body) throws Exception {
        ContentDefinition definition =
                new ContentDefinition(
                        DefinitionId.of("move.test.training_slash"),
                        DefinitionType.MOVE,
                        1,
                        Path.of("training-slash.json"),
                        JSON.readTree(body),
                        List.of());
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
                        Map.of("moves", 1)),
                DefinitionRegistry.of(List.of(definition)),
                ReferenceIndex.of(List.of()));
    }

    static String validBody() {
        return """
                {
                  "family": "SWORD",
                  "input": {
                    "action": "PRIMARY",
                    "direction": "NEUTRAL",
                    "branch": "PRIMARY_1"
                  },
                  "phases": {
                    "windup_ticks": 4,
                    "active_ticks": 3,
                    "recovery_ticks": 7
                  },
                  "commit_tick": 3,
                  "costs": {
                    "stamina": 12,
                    "mana": 0,
                    "health": 0,
                    "setup_stamina": 0
                  },
                  "movement": {
                    "curve": "STEP",
                    "facing_turn_degrees": 15
                  },
                  "hitboxes": [{
                    "tick": 4,
                    "shape": "ARC",
                    "range": 2.8,
                    "angle_degrees": 95,
                    "height": 2.0,
                    "max_targets": 4,
                    "hit_group": "PRIMARY"
                  }],
                  "outputs": {
                    "health": {
                      "physical_type": "SLASH",
                      "move_coefficient": 0.8
                    },
                    "posture": 12,
                    "guard_pressure": 9
                  },
                  "cancels": {
                    "dodge_from_tick": 9,
                    "chain_windows": [{
                      "from_tick": 7,
                      "to_tick": 11,
                      "branch": "PRIMARY_2"
                    }]
                  },
                  "interrupt_resistance": "NONE",
                  "presentation": {
                    "archetype": "SWORD_HORIZONTAL_SLASH"
                  },
                  "profiles": {
                    "pve_multiplier": 1.0,
                    "pvp_multiplier": 0.65
                  }
                }
                """;
    }
}
