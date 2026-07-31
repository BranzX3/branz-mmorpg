package com.branz.mmorpg.items.definition;

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

class ItemEngineTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void compilesUniqueAndLotDefinitionsFromTheImmutableSnapshot() throws Exception {
        ContentSnapshot snapshot =
                snapshot(
                        definition(
                                "weapon.test.blade",
                                """
                                {
                                  "asset_id": "weapon.test.blade",
                                  "item_class": "UNIQUE_DURABLE",
                                  "base_max_durability": 120,
                                  "weapon_profile": {
                                    "family": "SWORD",
                                    "power": 100
                                  }
                                }
                                """),
                        definition(
                                "material.test.ore",
                                """
                                {
                                  "asset_id": "material.test.ore",
                                  "item_class": "STACKABLE_LOT"
                                }
                                """));

        Result<ItemEngine, ItemEngineErrorCode> result = ItemEngine.compile(snapshot);

        assertTrue(result.isSuccess());
        ItemEngine engine = ((Result.Success<ItemEngine, ItemEngineErrorCode>) result).value();
        assertEquals(2, engine.all().size());
        ItemDefinition weapon = engine.find(DefinitionId.of("weapon.test.blade")).orElseThrow();
        assertEquals(ItemClass.UNIQUE_DURABLE, weapon.itemClass());
        assertEquals(120, weapon.baseMaxDurability().orElseThrow());
        assertEquals("SWORD", weapon.weaponProfile().orElseThrow().family());
        assertEquals(100, weapon.weaponProfile().orElseThrow().power());
        assertFalse(engine.find(DefinitionId.of("material.test.ore")).orElseThrow().cosmetic());
    }

    @Test
    void rejectsCosmeticDurabilityWithoutActivatingAPartialEngine() throws Exception {
        ContentSnapshot snapshot =
                snapshot(
                        definition(
                                "cosmetic.test.hat",
                                """
                                {
                                  "asset_id": "cosmetic.test.hat",
                                  "item_class": "UNIQUE_DURABLE",
                                  "base_max_durability": 1
                                }
                                """));

        Result<ItemEngine, ItemEngineErrorCode> result = ItemEngine.compile(snapshot);

        assertFalse(result.isSuccess());
        assertEquals(
                ItemEngineErrorCode.ITEM_DURABILITY_INVALID,
                ((Result.Failure<ItemEngine, ItemEngineErrorCode>) result).error());
    }

    @Test
    void compilesBowHandlingAndRejectsMissingBowContract() throws Exception {
        String valid =
                """
                {
                  "asset_id": "weapon.test.bow",
                  "item_class": "UNIQUE_DURABLE",
                  "base_max_durability": 100,
                  "weapon_profile": {
                    "family": "BOW",
                    "power": 90,
                    "bow": {
                      "minimum_draw_ticks": 5,
                      "full_draw_ticks": 20,
                      "free_full_draw_hold_ticks": 60,
                      "strain_stamina_per_second": 4,
                      "minimum_velocity_multiplier": 0.55,
                      "minimum_posture_multiplier": 0.5,
                      "maximum_penetration_percentage": 0.2
                    }
                  }
                }
                """;

        Result<ItemEngine, ItemEngineErrorCode> compiled =
                ItemEngine.compile(snapshot(definition("weapon.test.bow", valid)));
        String missing = valid.replace("\"bow\": {", "\"ignored\": {");
        Result<ItemEngine, ItemEngineErrorCode> rejected =
                ItemEngine.compile(snapshot(definition("weapon.test.bow", missing)));

        BowWeaponProfile bow =
                ((Result.Success<ItemEngine, ItemEngineErrorCode>) compiled)
                        .value()
                        .find(DefinitionId.of("weapon.test.bow"))
                        .orElseThrow()
                        .weaponProfile()
                        .orElseThrow()
                        .bowProfile()
                        .orElseThrow();
        assertEquals(5, bow.minimumDrawTicks());
        assertEquals(60, bow.freeFullDrawHoldTicks());
        assertEquals(0.2, bow.maximumPenetrationPercentage());
        assertEquals(
                ItemEngineErrorCode.ITEM_WEAPON_PROFILE_INVALID,
                ((Result.Failure<ItemEngine, ItemEngineErrorCode>) rejected).error());
    }

    @Test
    void compilesAmmoAndQuiverProfilesAndRejectsMissingAmmoFamily() throws Exception {
        ContentSnapshot valid =
                snapshot(
                        definition(
                                "ammo.test.arrow",
                                """
                                {
                                  "asset_id": "ammo.test.arrow",
                                  "item_class": "STACKABLE_LOT",
                                  "ammo_profile": {"family": "ARROW"}
                                }
                                """),
                        definition(
                                "equipment.test.quiver",
                                """
                                {
                                  "asset_id": "equipment.test.quiver",
                                  "item_class": "UNIQUE_DURABLE",
                                  "quiver_profile": {
                                    "capacity": 96,
                                    "supported_ammo_families": ["ARROW"],
                                    "prepared_ammo_category_count": 4,
                                    "ammo_switch_handling_ticks": 6
                                  }
                                }
                                """));

        Result<ItemEngine, ItemEngineErrorCode> compiled = ItemEngine.compile(valid);
        Result<ItemEngine, ItemEngineErrorCode> missing =
                ItemEngine.compile(
                        snapshot(
                                definition(
                                        "ammo.test.missing",
                                        """
                                        {
                                          "asset_id": "ammo.test.missing",
                                          "item_class": "STACKABLE_LOT"
                                        }
                                        """)));

        ItemEngine engine = ((Result.Success<ItemEngine, ItemEngineErrorCode>) compiled).value();
        assertEquals(
                AmmoFamily.ARROW,
                engine.find(DefinitionId.of("ammo.test.arrow"))
                        .orElseThrow()
                        .ammoProfile()
                        .orElseThrow()
                        .family());
        QuiverProfile quiver =
                engine.find(DefinitionId.of("equipment.test.quiver"))
                        .orElseThrow()
                        .quiverProfile()
                        .orElseThrow();
        assertEquals(96, quiver.capacity());
        assertEquals(4, quiver.preparedAmmoCategoryCount());
        assertEquals(6, quiver.ammoSwitchHandlingTicks());
        assertTrue(quiver.supports(new AmmoProfile(AmmoFamily.ARROW)));
        assertEquals(
                ItemEngineErrorCode.ITEM_AMMO_PROFILE_INVALID,
                ((Result.Failure<ItemEngine, ItemEngineErrorCode>) missing).error());
    }

    @Test
    void compilesCrossbowCheckpointsAndRejectsMissingCrossbowContract() throws Exception {
        String valid =
                """
                {
                  "asset_id": "weapon.test.crossbow",
                  "item_class": "UNIQUE_DURABLE",
                  "base_max_durability": 120,
                  "weapon_profile": {
                    "family": "CROSSBOW",
                    "power": 110,
                    "crossbow": {
                      "bolt_placement_ticks": 12,
                      "locking_ticks": 8
                    }
                  }
                }
                """;

        Result<ItemEngine, ItemEngineErrorCode> compiled =
                ItemEngine.compile(snapshot(definition("weapon.test.crossbow", valid)));
        Result<ItemEngine, ItemEngineErrorCode> rejected =
                ItemEngine.compile(
                        snapshot(
                                definition(
                                        "weapon.test.crossbow",
                                        valid.replace("\"crossbow\": {", "\"ignored\": {"))));

        CrossbowWeaponProfile crossbow =
                ((Result.Success<ItemEngine, ItemEngineErrorCode>) compiled)
                        .value()
                        .find(DefinitionId.of("weapon.test.crossbow"))
                        .orElseThrow()
                        .weaponProfile()
                        .orElseThrow()
                        .crossbowProfile()
                        .orElseThrow();
        assertEquals(12, crossbow.boltPlacementTicks());
        assertEquals(8, crossbow.lockingTicks());
        assertEquals(
                ItemEngineErrorCode.ITEM_WEAPON_PROFILE_INVALID,
                ((Result.Failure<ItemEngine, ItemEngineErrorCode>) rejected).error());
    }

    private static ContentDefinition definition(String id, String body) throws Exception {
        return new ContentDefinition(
                DefinitionId.of(id),
                DefinitionType.ITEM,
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
                        Map.of("items", definitions.length)),
                DefinitionRegistry.of(List.of(definitions)),
                ReferenceIndex.of(List.of()));
    }
}
