package com.branz.mmorpg.magic.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.definition.DefinitionRegistry;
import com.branz.mmorpg.content.manifest.ContentManifest;
import com.branz.mmorpg.content.reference.ReferenceIndex;
import com.branz.mmorpg.content.schema.DefinitionType;
import com.branz.mmorpg.content.snapshot.ContentLoadFailure;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.content.snapshot.ContentSnapshotLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpellEngineTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void compilesEmberProjectileFromImmutableSnapshot() throws Exception {
        Result<SpellEngine, SpellEngineErrorCode> result =
                SpellEngine.compile(snapshot(validBody()));

        assertTrue(result.isSuccess());
        SpellDefinition spell =
                ((Result.Success<SpellEngine, SpellEngineErrorCode>) result)
                        .value()
                        .find(DefinitionId.of("spell.ember.fire_lance"))
                        .orElseThrow();
        assertEquals(SpellCastType.CHARGE, spell.castType());
        assertEquals(Set.of("STAFF", "EMBER"), spell.requirements().catalystTags());
        assertEquals(18, spell.manaCost());
        assertEquals(ArcaneSchool.FIRE, spell.output().arcaneSchool());
        assertEquals(70, spell.projectile().orElseThrow().lifetimeTicks());
    }

    @Test
    void rejectsProjectileDeliveryWithoutProjectileFields() throws Exception {
        Result<SpellEngine, SpellEngineErrorCode> result =
                SpellEngine.compile(
                        snapshot(validBody().replace("\"projectile\": {", "\"ignored\": {")));

        assertEquals(
                SpellEngineErrorCode.SPELL_FIELD_INVALID,
                ((Result.Failure<SpellEngine, SpellEngineErrorCode>) result).error());
    }

    @Test
    void rejectsProjectileFieldsForAnotherDelivery() throws Exception {
        Result<SpellEngine, SpellEngineErrorCode> result =
                SpellEngine.compile(snapshot(validBody().replace("\"PROJECTILE\"", "\"DIRECT\"")));

        assertEquals(
                SpellEngineErrorCode.SPELL_FIELD_INVALID,
                ((Result.Failure<SpellEngine, SpellEngineErrorCode>) result).error());
    }

    @Test
    void compilesCompleteEmberAndRunicTrainingFixture() {
        Path fixture = Path.of("..", "example-content", "milestone-1").toAbsolutePath().normalize();
        Result<ContentSnapshot, ContentLoadFailure> loaded =
                new ContentSnapshotLoader().load(fixture);
        assertTrue(loaded.isSuccess());

        Result<SpellEngine, SpellEngineErrorCode> compiled =
                SpellEngine.compile(
                        ((Result.Success<ContentSnapshot, ContentLoadFailure>) loaded).value());

        assertTrue(compiled.isSuccess());
        SpellEngine engine = ((Result.Success<SpellEngine, SpellEngineErrorCode>) compiled).value();
        assertEquals(5, engine.all().size());
        assertTrue(
                engine.find(DefinitionId.of("spell.ember.cinder_snap"))
                        .orElseThrow()
                        .direct()
                        .isPresent());
        assertTrue(
                engine.find(DefinitionId.of("spell.ember.fire_lance"))
                        .orElseThrow()
                        .projectile()
                        .isPresent());
        assertTrue(
                engine.find(DefinitionId.of("spell.ember.scorching_ground"))
                        .orElseThrow()
                        .zone()
                        .isPresent());
        assertTrue(
                engine.find(DefinitionId.of("spell.ember.flame_torrent"))
                        .orElseThrow()
                        .channel()
                        .isPresent());
        assertTrue(
                engine.find(DefinitionId.of("spell.runic.ember_edge"))
                        .orElseThrow()
                        .imbuement()
                        .isPresent());
    }

    private static String validBody() {
        return """
                {
                  "art": "magic.ember",
                  "cast_type": "CHARGE",
                  "target_type": "CROSSHAIR_POINT",
                  "delivery": "PROJECTILE",
                  "requirements": {"catalyst_tags": ["STAFF", "EMBER"], "attunement": 2},
                  "cost": {"mana": 18},
                  "phases": {"windup_ticks": 8, "minimum_charge_ticks": 8,
                             "maximum_charge_ticks": 30, "recovery_ticks": 12},
                  "interruption": {"movement": false, "damage": false, "flinch": true,
                                   "stagger": true, "silence": true, "weapon_swap": true},
                  "projectile": {"speed": 2.2, "gravity_per_tick": 0.01,
                                 "drag_per_tick": 0.995, "collision_radius": 0.22,
                                 "lifetime_ticks": 70, "pierce_count": 0,
                                 "hit_group": "EMBER_FIRE_LANCE"},
                  "output": {"arcane_school": "FIRE", "power_coefficient": 0.9,
                             "posture": 16, "guard_pressure": 14},
                  "presentation": {"archetype": "EMBER_FIRE_LANCE"},
                  "profiles": {"pve_multiplier": 1.0, "pvp_multiplier": 0.65}
                }
                """;
    }

    private static ContentSnapshot snapshot(String body) throws Exception {
        ContentDefinition definition =
                new ContentDefinition(
                        DefinitionId.of("spell.ember.fire_lance"),
                        DefinitionType.SPELL,
                        1,
                        Path.of("spell.json"),
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
                        Map.of("spells", 1)),
                DefinitionRegistry.of(List.of(definition)),
                ReferenceIndex.of(List.of()));
    }
}
