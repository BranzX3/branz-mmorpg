package com.branz.mmorpg.content.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonSchemaGeneratorTest {
    @TempDir Path outputDirectory;

    @Test
    void emitsRangesUnitsEnumsAndCombinedSchema() throws IOException {
        JsonSchemaGenerator generator = new JsonSchemaGenerator();

        Map<String, ObjectNode> schemas = generator.generate();
        generator.write(outputDirectory);

        ObjectNode move = schemas.get("move.schema.json");
        assertEquals(
                8.0,
                move.at("/properties/hitboxes/items/properties/max_targets/maximum").doubleValue());
        assertEquals(
                "targets",
                move.at("/properties/hitboxes/items/properties/max_targets/x-unit").asText());
        assertTrue(
                schemas.get("mount.schema.json")
                        .at("/properties/permanent_death/enum/0")
                        .isBoolean());
        ObjectNode spell = schemas.get("spell.schema.json");
        assertEquals(
                8.0, spell.at("/properties/projectile/properties/speed/maximum").doubleValue());
        assertTrue(
                spell.at("/properties/requirements/required").toString().contains("catalyst_tags"));
        assertEquals(
                1.0,
                schemas.get("item.schema.json")
                        .at("/properties/catalyst_profile/properties/channel_stability/maximum")
                        .doubleValue());
        assertEquals(
                DefinitionType.values().length,
                schemas.get("content-definition.schema.json").path("oneOf").size());
        assertTrue(Files.isRegularFile(outputDirectory.resolve("city.schema.json")));
    }
}
