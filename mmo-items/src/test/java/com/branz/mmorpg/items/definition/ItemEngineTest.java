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
                                  "base_max_durability": 120
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
