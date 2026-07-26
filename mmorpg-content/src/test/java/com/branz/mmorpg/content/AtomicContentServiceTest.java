package com.branz.mmorpg.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicContentServiceTest {
    @TempDir
    Path directory;

    @Test
    void loadsTypedMaterialDefinition() throws IOException {
        write("ore.yml", validMaterial("branz:aether_ore"));
        AtomicContentService service = new AtomicContentService();

        var result = service.reload(directory);

        assertTrue(result.successful());
        assertEquals(1, result.definitionCount());
        assertTrue(service.snapshot().materials().containsKey(ContentId.parse("branz:aether_ore")));
    }

    @Test
    void failedReloadKeepsLastValidSnapshot() throws IOException {
        Path file = write("ore.yml", validMaterial("branz:aether_ore"));
        AtomicContentService service = new AtomicContentService();
        assertTrue(service.reload(directory).successful());
        long validRevision = service.snapshot().revision();
        Files.writeString(file, "type: material\nid: invalid\n");

        var result = service.reload(directory);

        assertFalse(result.successful());
        assertEquals(validRevision, service.snapshot().revision());
        assertEquals(1, service.snapshot().definitions().size());
    }

    @Test
    void rejectsDuplicateIds() throws IOException {
        write("one.yml", validMaterial("branz:aether_ore"));
        write("two.yml", validMaterial("branz:aether_ore"));

        var result = new AtomicContentService().reload(directory);

        assertFalse(result.successful());
        assertTrue(result.diagnostics().stream().anyMatch(line -> line.contains("duplicate content ID")));
    }

    @Test
    void loadsAndValidatesADeclarativeSkillGraph() throws IOException {
        write("slash.yml", """
                type: skill
                id: branz:heavy_slash
                display-name: Heavy Slash
                input-slot: weapon-1
                tags: [weapon, sword]
                cast-ms: 250
                active-ms: 50
                recovery-ms: 300
                cooldown-ms: 2500
                cooldown-group: sword-primary
                costs: {STAMINA: 15}
                interrupt-refund: 0.5
                range: 4.5
                requires-line-of-sight: true
                root-effect: combo
                effects:
                  - id: combo
                    type: sequence
                    children: [hit]
                  - id: hit
                    type: damage
                    numbers: {power: 40}
                    values: {type: physical}
                """);

        AtomicContentService service = new AtomicContentService();
        var result = service.reload(directory);

        assertTrue(result.successful(), () -> String.join(", ", result.diagnostics()));
        assertEquals(1, service.snapshot().skills().size());
        assertEquals(2_500L,
                service.snapshot().skills().get(ContentId.parse("branz:heavy_slash")).cooldownMillis());
    }

    @Test
    void rejectsRecipesWhoseInputsAreOnlyProducedByACycle() throws IOException {
        write("a.yml", validMaterial("branz:a"));
        write("b.yml", validMaterial("branz:b"));
        write("recipe-a.yml", recipe("branz:recipe_a", "branz:b", "branz:a"));
        write("recipe-b.yml", recipe("branz:recipe_b", "branz:a", "branz:b"));

        var result = new AtomicContentService().reload(directory);

        assertFalse(result.successful());
        assertTrue(result.diagnostics().stream()
                .anyMatch(line -> line.contains("no active MMO acquisition path")));
    }

    @Test
    void loadsADeclarativeStatusWithTypedModifier() throws IOException {
        write("slow.yml", """
                type: status
                id: branz:slow
                display-name: Slow
                category: negative
                stack-policy: replace_weaker
                max-stacks: 1
                duration-ms: 4000
                periodic-interval-ms: 0
                potency: 0
                modifiers:
                  - id: movement_speed
                    attribute: movement_speed
                    operation: add_percent
                    value: -0.3
                    stacking-group: movement_slow
                    priority: 1
                dispel-tags: [movement]
                crowd-control: slow
                offline-policy: tick_down
                """);

        AtomicContentService service = new AtomicContentService();
        var result = service.reload(directory);

        assertTrue(result.successful(), () -> String.join(", ", result.diagnostics()));
        var status = service.snapshot().statuses().get(ContentId.parse("branz:slow"));
        assertEquals(1, status.modifiers().size());
        assertEquals(com.branz.mmorpg.api.stat.AttributeType.MOVEMENT_SPEED,
                status.modifiers().get(0).attribute());
    }

    @Test
    void rejectsClassContentWithMissingStarterReferences() throws IOException {
        write("warrior.yml", """
                type: character_class
                id: branz:warrior
                display-name: Warrior
                schema-version: 1
                roles: [damage]
                base-attributes: {max_health: 140, max_stamina: 100}
                primary-resource: stamina
                secondary-resources: []
                allowed-weapon-tags: [sword]
                allowed-armor-tags: [heavy]
                class-skills: [branz:missing_skill]
                ultimate-skill: branz:missing_ultimate
                passive-root-node: branz:warrior_root
                starter-grant:
                  id: branz:warrior_starter
                  revision: 1
                  weapon: branz:missing_weapon
                  unlocked-skills: [branz:missing_skill]
                  additional-items: {}
                tags: [physical]
                """);

        var result = new AtomicContentService().reload(directory);

        assertFalse(result.successful());
        assertTrue(result.diagnostics().stream()
                .anyMatch(line -> line.contains("unknown starter weapon branz:missing_weapon")));
        assertTrue(result.diagnostics().stream()
                .anyMatch(line -> line.contains("unknown class skill branz:missing_skill")));
    }

    private Path write(String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private String validMaterial(String id) {
        return """
                type: material
                id: %s
                display-name: Aether Ore
                category: crafting_material
                rarity: uncommon
                tradable: true
                max-stack-size: 64
                """.formatted(id);
    }

    private static String recipe(String id, String input, String output) {
        return """
                type: recipe
                id: %s
                display_name: Cycle
                inputs: {"%s": 1}
                optional_catalysts: {}
                coin_fee: 0
                station_tag: branz:forge
                required_profession_level: 1
                duration_ms: 0
                output:
                  item: %s
                  quantity: 1
                  binding: unbound
                  quality_policy: fixed
                profession_xp: 0
                trivial_after_level: 1
                """.formatted(id, input, output);
    }
}
