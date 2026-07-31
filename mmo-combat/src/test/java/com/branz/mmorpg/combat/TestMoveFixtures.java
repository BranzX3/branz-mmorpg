package com.branz.mmorpg.combat;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.input.DirectionSnapshot;
import com.branz.mmorpg.combat.input.SemanticInput;
import com.branz.mmorpg.combat.move.MoveDefinition;
import com.branz.mmorpg.combat.move.MoveDefinition.CancelWindows;
import com.branz.mmorpg.combat.move.MoveDefinition.ChainWindow;
import com.branz.mmorpg.combat.move.MoveDefinition.CombatProfiles;
import com.branz.mmorpg.combat.move.MoveDefinition.Hitbox;
import com.branz.mmorpg.combat.move.MoveDefinition.HitboxShape;
import com.branz.mmorpg.combat.move.MoveDefinition.InputBranch;
import com.branz.mmorpg.combat.move.MoveDefinition.Movement;
import com.branz.mmorpg.combat.move.MoveDefinition.Outputs;
import com.branz.mmorpg.combat.move.MoveDefinition.PhaseDurations;
import com.branz.mmorpg.combat.move.MoveDefinition.PhysicalDamageType;
import com.branz.mmorpg.combat.move.MoveDefinition.ResourceCost;
import com.branz.mmorpg.combat.move.MoveEngine;
import com.branz.mmorpg.combat.move.MoveEngineErrorCode;
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

public final class TestMoveFixtures {
    private TestMoveFixtures() {}

    public static MoveDefinition trainingSlash() {
        return trainingSlash(new ResourceCost(12, 0, 0, 0));
    }

    public static MoveDefinition trainingSlash(ResourceCost cost) {
        return new MoveDefinition(
                DefinitionId.of("move.test.training_slash"),
                "SWORD",
                new InputBranch(SemanticInput.PRIMARY, DirectionSnapshot.NEUTRAL, "PRIMARY_1"),
                new PhaseDurations(4, 3, 7),
                3,
                cost,
                new Movement("STEP", 15),
                List.of(new Hitbox(4, HitboxShape.ARC, 2.8, 95, 2, 4, "PRIMARY")),
                new Outputs(PhysicalDamageType.SLASH, 0.8, 12, 9),
                new CancelWindows(9, List.of(new ChainWindow(7, 11, "PRIMARY_2"))),
                "NONE",
                "SWORD_HORIZONTAL_SLASH",
                new CombatProfiles(1.0, 0.65));
    }

    public static MoveEngine moveEngine() {
        try {
            ContentDefinition definition =
                    new ContentDefinition(
                            DefinitionId.of("move.test.training_slash"),
                            DefinitionType.MOVE,
                            1,
                            Path.of("training-slash.json"),
                            new ObjectMapper().readTree(validBody()),
                            List.of());
            ContentSnapshot snapshot =
                    new ContentSnapshot(
                            new ContentManifest(
                                    "content.test",
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
            Result<MoveEngine, MoveEngineErrorCode> result = MoveEngine.compile(snapshot);
            if (result instanceof Result.Success<MoveEngine, MoveEngineErrorCode> success) {
                return success.value();
            }
            Result.Failure<MoveEngine, MoveEngineErrorCode> failure =
                    (Result.Failure<MoveEngine, MoveEngineErrorCode>) result;
            throw new IllegalStateException(failure.error() + ": " + failure.detail());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Invalid test move JSON", exception);
        }
    }

    private static String validBody() {
        return """
                {
                  "family":"SWORD",
                  "input":{"action":"PRIMARY","direction":"NEUTRAL","branch":"PRIMARY_1"},
                  "phases":{"windup_ticks":4,"active_ticks":3,"recovery_ticks":7},
                  "commit_tick":3,
                  "costs":{"stamina":12,"mana":0,"health":0,"setup_stamina":0},
                  "movement":{"curve":"STEP","facing_turn_degrees":15},
                  "hitboxes":[{
                    "tick":4,"shape":"ARC","range":2.8,"angle_degrees":95,
                    "height":2.0,"max_targets":4,"hit_group":"PRIMARY"
                  }],
                  "outputs":{
                    "health":{"physical_type":"SLASH","move_coefficient":0.8},
                    "posture":12,"guard_pressure":9
                  },
                  "cancels":{
                    "dodge_from_tick":9,
                    "chain_windows":[{"from_tick":7,"to_tick":11,"branch":"PRIMARY_2"}]
                  },
                  "interrupt_resistance":"NONE",
                  "presentation":{"archetype":"SWORD_HORIZONTAL_SLASH"},
                  "profiles":{"pve_multiplier":1.0,"pvp_multiplier":0.65}
                }
                """;
    }
}
