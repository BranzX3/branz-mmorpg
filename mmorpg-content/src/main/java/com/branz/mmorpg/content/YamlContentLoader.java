package com.branz.mmorpg.content;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.skill.ResourceType;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.api.skill.SkillEffectNode;
import com.branz.mmorpg.api.skill.SkillEffectType;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.mastery.MasteryNodeDefinition;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.item.LootDefinition;
import com.branz.mmorpg.api.item.LootEntry;
import com.branz.mmorpg.api.gathering.GatheringNodeDefinition;
import com.branz.mmorpg.api.gathering.GatheringYieldDefinition;
import com.branz.mmorpg.api.crafting.ProfessionDefinition;
import com.branz.mmorpg.api.crafting.RecipeDefinition;
import com.branz.mmorpg.api.mob.MobDefinition;
import com.branz.mmorpg.api.mob.MobAbilityDefinition;
import com.branz.mmorpg.api.encounter.EncounterDefinition;
import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.CharacterClassId;
import com.branz.mmorpg.api.character.CharacterClassRole;
import com.branz.mmorpg.api.character.StarterGrantPlan;
import com.branz.mmorpg.api.character.ClassSkillNodeDefinition;
import com.branz.mmorpg.api.character.ClassSkillNodeType;
import com.branz.mmorpg.api.stat.AttributeType;
import com.branz.mmorpg.api.stat.AttributeModifier;
import com.branz.mmorpg.api.stat.ModifierOperation;
import com.branz.mmorpg.api.stat.ModifierSource;
import com.branz.mmorpg.api.status.CrowdControlCategory;
import com.branz.mmorpg.api.status.OfflinePolicy;
import com.branz.mmorpg.api.status.StackPolicy;
import com.branz.mmorpg.api.status.StatusCategory;
import com.branz.mmorpg.api.status.StatusDefinition;
import com.branz.mmorpg.api.input.CombatInputKey;
import com.branz.mmorpg.api.input.SkillSlot;
import com.branz.mmorpg.api.input.CombatInputProfileDefinition;
import com.branz.mmorpg.api.input.CombatComboDefinition;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

final class YamlContentLoader {
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    Map<ContentId, ContentDefinition> load(Path root) throws ContentLoadException {
        List<String> diagnostics = new ArrayList<>();
        Map<ContentId, ContentDefinition> definitions = new LinkedHashMap<>();

        if (!Files.isDirectory(root)) {
            throw new ContentLoadException(List.of("Content directory does not exist: " + root.toAbsolutePath()));
        }

        List<Path> files;
        try (Stream<Path> paths = Files.walk(root)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(this::isYaml)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException exception) {
            throw new ContentLoadException(List.of("Unable to scan content directory: " + exception.getMessage()));
        }

        if (files.isEmpty()) {
            diagnostics.add("No .yml or .yaml definitions found under " + root.toAbsolutePath());
        }

        for (Path file : files) {
            try {
                ContentDefinition definition = parse(file);
                ContentDefinition previous = definitions.putIfAbsent(definition.id(), definition);
                if (previous != null) {
                    diagnostics.add(relative(root, file) + ": duplicate content ID " + definition.id());
                }
            } catch (Exception exception) {
                diagnostics.add(relative(root, file) + ": " + rootCauseMessage(exception));
            }
        }

        validateLifeSkillReferences(definitions, diagnostics);
        validateCombatInputs(definitions, diagnostics);

        if (!diagnostics.isEmpty()) {
            throw new ContentLoadException(diagnostics);
        }
        return definitions;
    }

    private void validateLifeSkillReferences(Map<ContentId, ContentDefinition> definitions,
                                             List<String> diagnostics) {
        Map<ContentId, LifeSkillNodeDefinition> nodes = new LinkedHashMap<>();
        definitions.forEach((id, definition) -> {
            if (definition instanceof LifeSkillNodeDefinition node) {
                nodes.put(id, node);
            }
        });
        for (LifeSkillNodeDefinition node : nodes.values()) {
            if (!(definitions.get(node.skillId()) instanceof LifeSkillDefinition)) {
                diagnostics.add(node.id() + ": unknown Life Skill " + node.skillId());
            }
            node.prerequisites().forEach((required, rank) -> {
                LifeSkillNodeDefinition prerequisite = nodes.get(required);
                if (prerequisite == null) {
                    diagnostics.add(node.id() + ": unknown prerequisite " + required);
                } else if (!prerequisite.skillId().equals(node.skillId())) {
                    diagnostics.add(node.id() + ": prerequisite belongs to another Life Skill "
                            + required);
                } else if (rank > prerequisite.maximumRank()) {
                    diagnostics.add(node.id() + ": prerequisite rank exceeds maximum for " + required);
                }
            });
        }
        Set<ContentId> visiting = new java.util.HashSet<>();
        Set<ContentId> visited = new java.util.HashSet<>();
        for (ContentId nodeId : nodes.keySet()) {
            detectNodeCycle(nodeId, nodes, visiting, visited, diagnostics);
        }
        validateWeapons(definitions, diagnostics);
        validateMasteryNodes(definitions, diagnostics);
        validateLoot(definitions, diagnostics);
        validateGathering(definitions, diagnostics);
        validateRecipes(definitions, diagnostics);
        validateMobs(definitions, diagnostics);
        validateEncounters(definitions, diagnostics);
        validateCharacterClasses(definitions, diagnostics);
    }

    private void validateCharacterClasses(Map<ContentId, ContentDefinition> definitions,
                                          List<String> diagnostics) {
        List<CharacterClassDefinition> classes = definitions.values().stream()
                .filter(CharacterClassDefinition.class::isInstance)
                .map(CharacterClassDefinition.class::cast).toList();
        if (classes.isEmpty()) return;
        Set<ContentId> required = Set.of(CharacterClassId.WARRIOR.value(),
                CharacterClassId.MAGE.value(), CharacterClassId.ROGUE.value());
        Set<ContentId> actual = classes.stream().map(CharacterClassDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!actual.equals(required)) {
            diagnostics.add("character classes must be exactly " + required + "; found " + actual);
        }
        for (CharacterClassDefinition characterClass : classes) {
            characterClass.baseAttributes().keySet().forEach(key -> {
                try {
                    AttributeType.fromContentKey(key);
                } catch (IllegalArgumentException invalid) {
                    diagnostics.add(characterClass.id() + ": unknown base attribute " + key);
                }
            });
            Set<ResourceType> resources = new java.util.HashSet<>(characterClass.secondaryResources());
            resources.add(characterClass.primaryResource());
            resources.add(ResourceType.HEALTH);
            resources.forEach(resource -> {
                AttributeType maximum = AttributeType.maximumFor(resource);
                boolean declared = characterClass.baseAttributes().keySet().stream().anyMatch(key -> {
                    try {
                        return maximum == AttributeType.fromContentKey(key);
                    } catch (IllegalArgumentException ignored) {
                        return false;
                    }
                });
                if (!declared) {
                    diagnostics.add(characterClass.id() + ": missing base attribute "
                            + maximum.name().toLowerCase(Locale.ROOT));
                }
            });
            for (ContentId skill : characterClass.classSkillIds()) {
                if (!(definitions.get(skill) instanceof SkillDefinition)) {
                    diagnostics.add(characterClass.id() + ": unknown class skill " + skill);
                }
            }
            if (!(definitions.get(characterClass.ultimateSkillId()) instanceof SkillDefinition)) {
                diagnostics.add(characterClass.id() + ": unknown ultimate skill "
                        + characterClass.ultimateSkillId());
            }
            StarterGrantPlan starter = characterClass.starterGrantPlan();
            ContentDefinition weapon = definitions.get(starter.weaponId());
            if (!(weapon instanceof WeaponDefinition starterWeapon)) {
                diagnostics.add(characterClass.id() + ": unknown starter weapon " + starter.weaponId());
            } else if (starterWeapon.tags().stream().noneMatch(characterClass.allowedWeaponTags()::contains)) {
                diagnostics.add(characterClass.id() + ": starter weapon is incompatible " + starter.weaponId());
            }
            starter.unlockedSkillIds().forEach(skill -> {
                if (!(definitions.get(skill) instanceof SkillDefinition)) {
                    diagnostics.add(characterClass.id() + ": unknown starter skill " + skill);
                }
            });
            starter.additionalItems().keySet().forEach(item -> {
                ContentDefinition found = definitions.get(item);
                if (!(found instanceof MaterialDefinition) && !(found instanceof WeaponDefinition)) {
                    diagnostics.add(characterClass.id() + ": unknown starter item " + item);
                }
            });
        }
        validateClassSkillTrees(definitions, classes, diagnostics);
    }

    private void validateClassSkillTrees(Map<ContentId, ContentDefinition> definitions,
                                         List<CharacterClassDefinition> classes,
                                         List<String> diagnostics) {
        Map<ContentId, ClassSkillNodeDefinition> nodes = new LinkedHashMap<>();
        definitions.forEach((id, definition) -> {
            if (definition instanceof ClassSkillNodeDefinition node) nodes.put(id, node);
        });
        for (CharacterClassDefinition characterClass : classes) {
            ClassSkillNodeDefinition root = nodes.get(characterClass.passiveRootNodeId());
            if (root == null || !root.classId().equals(characterClass.id())) {
                diagnostics.add(characterClass.id() + ": missing class tree root "
                        + characterClass.passiveRootNodeId());
            }
        }
        for (ClassSkillNodeDefinition node : nodes.values()) {
            ContentDefinition owner = definitions.get(node.classId());
            if (!(owner instanceof CharacterClassDefinition characterClass)) {
                diagnostics.add(node.id() + ": unknown owning class " + node.classId());
                continue;
            }
            if (node.treeRevision() != characterClass.treeRevision()) {
                diagnostics.add(node.id() + ": tree revision does not match " + node.classId());
            }
            node.unlockedSkillId().ifPresent(skill -> {
                if (!(definitions.get(skill) instanceof SkillDefinition)
                        || (!characterClass.classSkillIds().contains(skill)
                        && !characterClass.ultimateSkillId().equals(skill))) {
                    diagnostics.add(node.id() + ": invalid class skill unlock " + skill);
                }
            });
            node.prerequisites().forEach((requiredId, rank) -> {
                ClassSkillNodeDefinition required = nodes.get(requiredId);
                if (required == null || !required.classId().equals(node.classId())) {
                    diagnostics.add(node.id() + ": invalid prerequisite " + requiredId);
                } else if (rank > required.maximumRank()) {
                    diagnostics.add(node.id() + ": prerequisite rank exceeds maximum " + requiredId);
                }
            });
        }
        Set<ContentId> visiting = new java.util.HashSet<>();
        Set<ContentId> visited = new java.util.HashSet<>();
        for (ContentId nodeId : nodes.keySet()) {
            detectClassNodeCycle(nodeId, nodes, visiting, visited, diagnostics);
        }
    }

    private void detectClassNodeCycle(ContentId nodeId,
                                      Map<ContentId, ClassSkillNodeDefinition> nodes,
                                      Set<ContentId> visiting, Set<ContentId> visited,
                                      List<String> diagnostics) {
        if (visited.contains(nodeId) || !nodes.containsKey(nodeId)) return;
        if (!visiting.add(nodeId)) {
            diagnostics.add(nodeId + ": class skill tree contains a cycle");
            return;
        }
        for (ContentId required : nodes.get(nodeId).prerequisites().keySet()) {
            detectClassNodeCycle(required, nodes, visiting, visited, diagnostics);
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
    }

    private void validateWeapons(Map<ContentId, ContentDefinition> definitions,
                                 List<String> diagnostics) {
        for (ContentDefinition definition : definitions.values()) {
            if (!(definition instanceof WeaponDefinition weapon)) {
                continue;
            }
            if (!(definitions.get(weapon.familyMasteryId()) instanceof MasteryDefinition family)
                    || family.kind() != MasteryDefinition.Kind.FAMILY) {
                diagnostics.add(weapon.id() + ": invalid family mastery " + weapon.familyMasteryId());
            }
            if (!(definitions.get(weapon.typeMasteryId()) instanceof MasteryDefinition type)
                    || type.kind() != MasteryDefinition.Kind.WEAPON_TYPE
                    || !weapon.familyMasteryId().equals(type.parentId())) {
                diagnostics.add(weapon.id() + ": invalid type mastery " + weapon.typeMasteryId());
            }
            if (!(definitions.get(weapon.basicAttackSkillId()) instanceof SkillDefinition)) {
                diagnostics.add(weapon.id() + ": unknown basic attack " + weapon.basicAttackSkillId());
            }
            for (ContentId skill : weapon.activeSkillIds()) {
                if (!(definitions.get(skill) instanceof SkillDefinition)) {
                    diagnostics.add(weapon.id() + ": unknown active skill " + skill);
                }
            }
        }
    }

    private void validateLoot(Map<ContentId, ContentDefinition> definitions,
                              List<String> diagnostics) {
        definitions.values().stream().filter(LootDefinition.class::isInstance)
                .map(LootDefinition.class::cast)
                .forEach(table -> table.entries().forEach(entry -> {
                    ContentDefinition item = definitions.get(entry.itemId());
                    if (!(item instanceof MaterialDefinition)
                            && !(item instanceof WeaponDefinition)) {
                        diagnostics.add(table.id() + ": unknown or unsupported loot item "
                                + entry.itemId());
                    }
                }));
    }

    private void validateGathering(Map<ContentId, ContentDefinition> definitions,
                                   List<String> diagnostics) {
        definitions.values().stream().filter(GatheringNodeDefinition.class::isInstance)
                .map(GatheringNodeDefinition.class::cast)
                .forEach(node -> {
                    if (!(definitions.get(node.skillId()) instanceof LifeSkillDefinition)) {
                        diagnostics.add(node.id() + ": unknown Life Skill " + node.skillId());
                    }
                    node.yields().forEach(yield -> {
                        if (!(definitions.get(yield.itemId()) instanceof MaterialDefinition)) {
                            diagnostics.add(node.id() + ": unknown material yield "
                                    + yield.itemId());
                        }
                    });
                });
    }

    private void validateRecipes(Map<ContentId, ContentDefinition> definitions,
                                 List<String> diagnostics) {
        Set<ContentId> gathered = definitions.values().stream()
                .filter(GatheringNodeDefinition.class::isInstance)
                .map(GatheringNodeDefinition.class::cast)
                .flatMap(node -> node.yields().stream())
                .map(GatheringYieldDefinition::itemId)
                .collect(java.util.stream.Collectors.toSet());
        List<RecipeDefinition> recipes = definitions.values().stream()
                .filter(RecipeDefinition.class::isInstance)
                .map(RecipeDefinition.class::cast)
                .toList();
        Set<ContentId> acquired = new java.util.HashSet<>(gathered);
        boolean changed;
        do {
            changed = false;
            for (RecipeDefinition recipe : recipes) {
                if (acquired.containsAll(recipe.inputs().keySet())) {
                    changed |= acquired.add(recipe.output().itemId());
                }
            }
        } while (changed);
        recipes.stream()
                .forEach(recipe -> {
                    recipe.professionId().ifPresent(profession -> {
                        if (!(definitions.get(profession) instanceof ProfessionDefinition)) {
                            diagnostics.add(recipe.id() + ": unknown profession " + profession);
                        }
                    });
                    java.util.stream.Stream.concat(
                                    recipe.inputs().keySet().stream(),
                                    recipe.optionalCatalysts().keySet().stream())
                            .forEach(item -> {
                                if (!(definitions.get(item) instanceof MaterialDefinition)) {
                                    diagnostics.add(recipe.id() + ": unknown material input " + item);
                                } else if (!acquired.contains(item)) {
                                    diagnostics.add(recipe.id()
                                            + ": input has no active MMO acquisition path " + item);
                                }
                            });
                    ContentDefinition output = definitions.get(recipe.output().itemId());
                    if (!(output instanceof MaterialDefinition)
                            && !(output instanceof WeaponDefinition)) {
                        diagnostics.add(recipe.id() + ": unsupported output "
                                + recipe.output().itemId());
                    }
                });
    }

    private void validateMobs(Map<ContentId, ContentDefinition> definitions,
                              List<String> diagnostics) {
        definitions.values().stream().filter(MobDefinition.class::isInstance)
                .map(MobDefinition.class::cast)
                .forEach(mob -> {
                    if (!(definitions.get(mob.lootTableId()) instanceof LootDefinition)) {
                        diagnostics.add(mob.id() + ": unknown loot table " + mob.lootTableId());
                    }
                    mob.abilities().forEach(ability -> {
                        if (!(definitions.get(ability.skillId()) instanceof SkillDefinition)) {
                            diagnostics.add(mob.id() + ": unknown ability " + ability.skillId());
                        }
                    });
                });
    }

    private void validateEncounters(Map<ContentId, ContentDefinition> definitions,
                                    List<String> diagnostics) {
        definitions.values().stream().filter(EncounterDefinition.class::isInstance)
                .map(EncounterDefinition.class::cast).forEach(encounter -> {
                    if (!(definitions.get(encounter.bossMobId()) instanceof MobDefinition)) {
                        diagnostics.add(encounter.id() + ": unknown boss mob "
                                + encounter.bossMobId());
                    }
                    if (!(definitions.get(encounter.rewardLootTableId()) instanceof LootDefinition)) {
                        diagnostics.add(encounter.id() + ": unknown reward loot table "
                                + encounter.rewardLootTableId());
                    }
                    encounter.phases().forEach(phase -> {
                        phase.abilityIds().forEach(skill -> {
                            if (!(definitions.get(skill) instanceof SkillDefinition)) {
                                diagnostics.add(encounter.id() + ": unknown phase ability " + skill);
                            }
                        });
                        phase.addMobIds().forEach(mob -> {
                            if (!(definitions.get(mob) instanceof MobDefinition)) {
                                diagnostics.add(encounter.id() + ": unknown phase add " + mob);
                            }
                        });
                    });
                });
    }

    private void detectNodeCycle(ContentId nodeId, Map<ContentId, LifeSkillNodeDefinition> nodes,
                                 Set<ContentId> visiting, Set<ContentId> visited,
                                 List<String> diagnostics) {
        if (visited.contains(nodeId) || !nodes.containsKey(nodeId)) {
            return;
        }
        if (!visiting.add(nodeId)) {
            diagnostics.add(nodeId + ": mastery tree contains a cycle");
            return;
        }
        for (ContentId prerequisite : nodes.get(nodeId).prerequisites().keySet()) {
            detectNodeCycle(prerequisite, nodes, visiting, visited, diagnostics);
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
    }

    private ContentDefinition parse(Path file) throws IOException {
        JsonNode root = mapper.readTree(file.toFile());
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("definition must be a YAML object");
        }
        JsonNode typeNode = root.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            throw new IllegalArgumentException("missing string field 'type'");
        }
        String type = typeNode.textValue().trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "material" -> {
                RawMaterialDefinition raw = mapper.treeToValue(root, RawMaterialDefinition.class);
                yield new MaterialDefinition(
                        ContentId.parse(raw.id()), raw.displayName(), raw.category(), raw.rarity(),
                        raw.tradable(), raw.maxStackSize());
            }
            case "skill" -> parseSkill(mapper.treeToValue(root, RawSkillDefinition.class));
            case "life_skill" -> parseLifeSkill(
                    mapper.treeToValue(root, RawLifeSkillDefinition.class));
            case "life_skill_node" -> parseLifeSkillNode(
                    mapper.treeToValue(root, RawLifeSkillNodeDefinition.class));
            case "combat_mastery" -> parseMastery(
                    mapper.treeToValue(root, RawMasteryDefinition.class));
            case "combat_mastery_node" -> parseMasteryNode(
                    mapper.treeToValue(root, RawMasteryNodeDefinition.class));
            case "weapon" -> parseWeapon(mapper.treeToValue(root, RawWeaponDefinition.class));
            case "loot_table" -> parseLoot(
                    mapper.treeToValue(root, RawLootDefinition.class));
            case "gathering_node" -> parseGathering(
                    mapper.treeToValue(root, RawGatheringNodeDefinition.class));
            case "profession" -> parseProfession(
                    mapper.treeToValue(root, RawProfessionDefinition.class));
            case "recipe" -> parseRecipe(
                    mapper.treeToValue(root, RawRecipeDefinition.class));
            case "mob" -> parseMob(mapper.treeToValue(root, RawMobDefinition.class));
            case "encounter" -> parseEncounter(
                    mapper.treeToValue(root, RawEncounterDefinition.class));
            case "character_class" -> parseCharacterClass(
                    mapper.treeToValue(root, RawCharacterClassDefinition.class));
            case "status" -> parseStatus(mapper.treeToValue(root, RawStatusDefinition.class));
            case "class_skill_node" -> parseClassSkillNode(
                    mapper.treeToValue(root, RawClassSkillNodeDefinition.class));
            case "combat_input_profile" -> parseCombatInputProfile(
                    mapper.treeToValue(root, RawCombatInputProfileDefinition.class));
            case "combat_combo" -> parseCombatCombo(
                    mapper.treeToValue(root, RawCombatComboDefinition.class));
            default -> throw new IllegalArgumentException("unsupported content type '" + type + "'");
        };
    }

    private void validateMasteryNodes(Map<ContentId, ContentDefinition> definitions,
                                      List<String> diagnostics) {
        Map<ContentId, MasteryNodeDefinition> nodes = new LinkedHashMap<>();
        definitions.forEach((id, definition) -> {
            if (definition instanceof MasteryNodeDefinition node) nodes.put(id, node);
        });
        for (MasteryNodeDefinition node : nodes.values()) {
            ContentDefinition owner = definitions.get(node.masteryId());
            if (!(owner instanceof MasteryDefinition mastery)) {
                diagnostics.add(node.id() + ": unknown mastery " + node.masteryId());
                continue;
            }
            if (node.treeRevision() != mastery.treeRevision()) {
                diagnostics.add(node.id() + ": tree revision does not match " + node.masteryId());
            }
            node.unlockedSkillId().ifPresent(skill -> {
                if (!(definitions.get(skill) instanceof SkillDefinition)) {
                    diagnostics.add(node.id() + ": unknown unlocked skill " + skill);
                }
            });
            node.prerequisites().forEach((requiredId, rank) -> {
                MasteryNodeDefinition required = nodes.get(requiredId);
                if (required == null || !required.masteryId().equals(node.masteryId())) {
                    diagnostics.add(node.id() + ": invalid mastery prerequisite " + requiredId);
                } else if (rank > required.maximumRank()) {
                    diagnostics.add(node.id() + ": prerequisite rank exceeds maximum " + requiredId);
                }
            });
        }
        Set<ContentId> visiting = new java.util.HashSet<>();
        Set<ContentId> visited = new java.util.HashSet<>();
        for (ContentId nodeId : nodes.keySet()) {
            detectMasteryNodeCycle(nodeId, nodes, visiting, visited, diagnostics);
        }
    }

    private void detectMasteryNodeCycle(ContentId nodeId,
                                        Map<ContentId, MasteryNodeDefinition> nodes,
                                        Set<ContentId> visiting, Set<ContentId> visited,
                                        List<String> diagnostics) {
        if (visited.contains(nodeId) || !nodes.containsKey(nodeId)) return;
        if (!visiting.add(nodeId)) {
            diagnostics.add(nodeId + ": combat mastery tree contains a cycle");
            return;
        }
        for (ContentId required : nodes.get(nodeId).prerequisites().keySet()) {
            detectMasteryNodeCycle(required, nodes, visiting, visited, diagnostics);
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
    }

    private StatusDefinition parseStatus(RawStatusDefinition raw) {
        ContentId id = ContentId.parse(raw.id());
        ModifierSource source = ModifierSource.of(ModifierSource.SourceType.STATUS, id.toString());
        List<AttributeModifier> modifiers = raw.modifiers() == null ? List.of()
                : raw.modifiers().stream().map(modifier -> new AttributeModifier(
                        modifier.id(), AttributeType.fromContentKey(modifier.attribute()),
                        ModifierOperation.valueOf(modifier.operation().toUpperCase(Locale.ROOT)),
                        modifier.value(), source, modifier.stackingGroup(), modifier.priority(),
                        Optional.<java.time.Instant>empty())).toList();
        return new StatusDefinition(id, raw.displayName(),
                StatusCategory.valueOf(raw.category().toUpperCase(Locale.ROOT)),
                StackPolicy.valueOf(raw.stackPolicy().toUpperCase(Locale.ROOT)),
                raw.maxStacks(), Duration.ofMillis(raw.durationMillis()),
                Duration.ofMillis(raw.periodicIntervalMillis()), raw.potency(), modifiers,
                raw.dispelTags() == null ? Set.of() : raw.dispelTags(),
                CrowdControlCategory.valueOf(raw.crowdControl().toUpperCase(Locale.ROOT)),
                OfflinePolicy.valueOf(raw.offlinePolicy().toUpperCase(Locale.ROOT)));
    }

    private ClassSkillNodeDefinition parseClassSkillNode(RawClassSkillNodeDefinition raw) {
        ContentId id = ContentId.parse(raw.id());
        ModifierSource source = ModifierSource.of(ModifierSource.SourceType.CLASS_TREE, id.toString());
        List<AttributeModifier> modifiers = raw.modifiers() == null ? List.of()
                : raw.modifiers().stream().map(modifier -> new AttributeModifier(
                        modifier.id(), AttributeType.fromContentKey(modifier.attribute()),
                        ModifierOperation.valueOf(modifier.operation().toUpperCase(Locale.ROOT)),
                        modifier.value(), source, modifier.stackingGroup(), modifier.priority(),
                        Optional.<java.time.Instant>empty())).toList();
        Map<ContentId, Integer> prerequisites = new LinkedHashMap<>();
        if (raw.prerequisites() != null) {
            raw.prerequisites().forEach((required, rank) ->
                    prerequisites.put(ContentId.parse(required), rank));
        }
        return new ClassSkillNodeDefinition(id, ContentId.parse(raw.classId()), raw.treeRevision(),
                raw.branchId(), ClassSkillNodeType.valueOf(raw.nodeType().toUpperCase(Locale.ROOT)),
                raw.maximumRank(), raw.pointCostPerRank(), raw.requiredClassLevel(), prerequisites,
                Optional.ofNullable(raw.exclusionGroup()),
                raw.unlockedSkill() == null || raw.unlockedSkill().isBlank()
                        ? Optional.empty() : Optional.of(ContentId.parse(raw.unlockedSkill())),
                modifiers);
    }

    private CombatInputProfileDefinition parseCombatInputProfile(
            RawCombatInputProfileDefinition raw) {
        Map<CombatInputKey, SkillSlot> bindings = new java.util.EnumMap<>(CombatInputKey.class);
        if (raw.bindings() != null) raw.bindings().forEach((input, slot) -> bindings.put(
                CombatInputKey.valueOf(input.toUpperCase(Locale.ROOT)),
                SkillSlot.valueOf(slot.toUpperCase(Locale.ROOT))));
        return new CombatInputProfileDefinition(ContentId.parse(raw.id()), raw.revision(), bindings,
                raw.comboWindowMillis(), raw.inputBufferMillis());
    }

    private CombatComboDefinition parseCombatCombo(RawCombatComboDefinition raw) {
        List<CombatComboDefinition.Step> steps = raw.steps() == null ? List.of()
                : raw.steps().stream().map(step -> new CombatComboDefinition.Step(
                        CombatInputKey.valueOf(step.input().toUpperCase(Locale.ROOT)),
                        step.minimumDelayMillis(), step.maximumDelayMillis())).toList();
        return new CombatComboDefinition(ContentId.parse(raw.id()),
                raw.requiredTags() == null ? Set.of() : raw.requiredTags(), steps,
                raw.resetTimeoutMillis(), raw.priority(), raw.consumesInput(),
                ContentId.parse(raw.resultSkill()));
    }

    private void validateCombatInputs(Map<ContentId, ContentDefinition> definitions,
                                      List<String> diagnostics) {
        List<CombatComboDefinition> combos = definitions.values().stream()
                .filter(CombatComboDefinition.class::isInstance)
                .map(CombatComboDefinition.class::cast).toList();
        for (CombatComboDefinition combo : combos) {
            if (!(definitions.get(combo.resultSkillId()) instanceof SkillDefinition)) {
                diagnostics.add(combo.id() + ": unknown combo result skill " + combo.resultSkillId());
            }
        }
        for (int left = 0; left < combos.size(); left++) {
            for (int right = left + 1; right < combos.size(); right++) {
                CombatComboDefinition first = combos.get(left);
                CombatComboDefinition second = combos.get(right);
                if (first.priority() == second.priority()
                        && first.requiredTags().equals(second.requiredTags())
                        && first.steps().stream().map(CombatComboDefinition.Step::input).toList()
                        .equals(second.steps().stream().map(CombatComboDefinition.Step::input).toList())) {
                    diagnostics.add(first.id() + ": ambiguous combo with " + second.id());
                }
            }
        }
    }

    private CharacterClassDefinition parseCharacterClass(RawCharacterClassDefinition raw) {
        if (raw.starterGrant() == null) {
            throw new IllegalArgumentException("character_class requires starter-grant");
        }
        Map<ContentId, Integer> additionalItems = new LinkedHashMap<>();
        if (raw.starterGrant().additionalItems() != null) {
            raw.starterGrant().additionalItems().forEach((id, quantity) ->
                    additionalItems.put(ContentId.parse(id), quantity));
        }
        StarterGrantPlan starter = new StarterGrantPlan(
                ContentId.parse(raw.starterGrant().id()), raw.starterGrant().revision(),
                ContentId.parse(raw.starterGrant().weapon()),
                raw.starterGrant().unlockedSkills() == null ? List.of()
                        : raw.starterGrant().unlockedSkills().stream().map(ContentId::parse).toList(),
                additionalItems);
        return new CharacterClassDefinition(
                ContentId.parse(raw.id()), raw.displayName(), raw.schemaVersion(),
                raw.roles() == null ? Set.of() : raw.roles().stream()
                        .map(role -> CharacterClassRole.valueOf(role.toUpperCase(Locale.ROOT)))
                        .collect(java.util.stream.Collectors.toSet()),
                raw.baseAttributes() == null ? Map.of() : raw.baseAttributes(),
                ResourceType.valueOf(raw.primaryResource().toUpperCase(Locale.ROOT)),
                raw.secondaryResources() == null ? Set.of() : raw.secondaryResources().stream()
                        .map(resource -> ResourceType.valueOf(resource.toUpperCase(Locale.ROOT)))
                        .collect(java.util.stream.Collectors.toSet()),
                raw.allowedWeaponTags() == null ? Set.of() : raw.allowedWeaponTags(),
                raw.allowedArmorTags() == null ? Set.of() : raw.allowedArmorTags(),
                raw.classSkills() == null ? List.of()
                        : raw.classSkills().stream().map(ContentId::parse).toList(),
                ContentId.parse(raw.ultimateSkill()), ContentId.parse(raw.passiveRootNode()),
                starter, raw.tags() == null ? Set.of() : raw.tags(),
                raw.maximumLevel() < 1 ? 100 : raw.maximumLevel(),
                raw.xpCurveBase() <= 0 ? 100.0 : raw.xpCurveBase(),
                raw.xpCurveExponent() <= 0 ? 1.65 : raw.xpCurveExponent(),
                raw.bonusSkillPointLevels() == null ? Set.of() : raw.bonusSkillPointLevels(),
                raw.treeRevision() < 1 ? 1 : raw.treeRevision());
    }

    private MobDefinition parseMob(RawMobDefinition raw) {
        if (raw.scaling() == null || raw.navigation() == null || raw.presentation() == null) {
            throw new IllegalArgumentException("mob requires scaling, navigation, and presentation");
        }
        List<MobAbilityDefinition> abilities = raw.abilities() == null ? List.of()
                : raw.abilities().stream().map(ability -> new MobAbilityDefinition(
                        ContentId.parse(ability.skill()), ability.weight(),
                        ability.minimumRange(), ability.maximumRange(),
                        ability.maximumHealthFraction(),
                        ability.requiredTargetTags() == null
                                ? Set.of() : ability.requiredTargetTags())).toList();
        Map<ContentId, Double> resistances = new LinkedHashMap<>();
        if (raw.statusResistances() != null) {
            raw.statusResistances().forEach((id, value) ->
                    resistances.put(ContentId.parse(id), value));
        }
        return new MobDefinition(
                ContentId.parse(raw.id()), raw.displayName(),
                raw.baseStats() == null ? Map.of() : raw.baseStats(),
                new MobDefinition.Scaling(raw.scaling().healthPerLevel(),
                        raw.scaling().powerPerLevel(), raw.scaling().maximumMultiplier()),
                raw.faction(), MobDefinition.TargetPolicy.valueOf(
                        raw.targetPolicy().toUpperCase(Locale.ROOT)),
                new MobDefinition.Navigation(raw.navigation().movementSpeed(),
                        raw.navigation().decisionIntervalMillis(),
                        raw.navigation().pathRequestIntervalMillis(),
                        raw.navigation().canSwim()),
                abilities, raw.aggroRange(), raw.leashRange(), raw.resetMillis(),
                raw.homeRegion() == null || raw.homeRegion().isBlank()
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(ContentId.parse(raw.homeRegion())),
                raw.statusImmunities() == null ? Set.of() : raw.statusImmunities().stream()
                        .map(ContentId::parse).collect(java.util.stream.Collectors.toSet()),
                resistances, ContentId.parse(raw.lootTable()), raw.minimumContribution(),
                new MobDefinition.Presentation(raw.presentation().entityType(),
                        java.util.Optional.ofNullable(raw.presentation().modelId())
                                .filter(value -> !value.isBlank())));
    }

    private EncounterDefinition parseEncounter(RawEncounterDefinition raw) {
        List<EncounterDefinition.Phase> phases = raw.phases() == null ? List.of()
                : raw.phases().stream().map(phase -> new EncounterDefinition.Phase(
                        phase.id(), phase.healthFractionThreshold(),
                        contentIds(phase.abilityIds()), contentIds(phase.addMobIds()),
                        phase.pressureMultiplier())).toList();
        return new EncounterDefinition(
                ContentId.parse(raw.id()), raw.displayName(),
                EncounterDefinition.Mode.valueOf(raw.mode().toUpperCase(Locale.ROOT)),
                ContentId.parse(raw.bossMob()), phases, raw.arenaRadius(),
                raw.preparationMillis(), raw.wipeGraceMillis(), raw.enrageMillis(),
                raw.minimumPlayers(), raw.maximumPlayers(), raw.minimumContribution(),
                EncounterDefinition.PartyPolicy.valueOf(
                        raw.partyPolicy().toUpperCase(Locale.ROOT)),
                raw.checkpointsAllowed(), ContentId.parse(raw.rewardLootTable()));
    }

    private static Set<ContentId> contentIds(Collection<String> values) {
        return values == null ? Set.of()
                : values.stream().map(ContentId::parse)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private ProfessionDefinition parseProfession(RawProfessionDefinition raw) {
        return new ProfessionDefinition(ContentId.parse(raw.id()), raw.displayName(),
                raw.maximumLevel(), raw.curveBase(), raw.curveExponent());
    }

    private RecipeDefinition parseRecipe(RawRecipeDefinition raw) {
        if (raw.output() == null) throw new IllegalArgumentException("recipe requires output");
        return new RecipeDefinition(
                ContentId.parse(raw.id()), raw.displayName(), contentAmounts(raw.inputs()),
                contentAmounts(raw.optionalCatalysts()), raw.coinFee(), raw.stationTag(),
                raw.profession() == null || raw.profession().isBlank()
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(ContentId.parse(raw.profession())),
                raw.requiredProfessionLevel(), raw.durationMillis(),
                new RecipeDefinition.Output(
                        ContentId.parse(raw.output().item()), raw.output().quantity(),
                        RecipeDefinition.Output.Binding.valueOf(
                                raw.output().binding().toUpperCase(Locale.ROOT)),
                        raw.output().qualityPolicy()),
                raw.professionXp(), raw.trivialAfterLevel());
    }

    private static Map<ContentId, Long> contentAmounts(Map<String, Long> raw) {
        if (raw == null) return Map.of();
        Map<ContentId, Long> result = new LinkedHashMap<>();
        raw.forEach((id, amount) -> result.put(ContentId.parse(id), amount));
        return result;
    }

    private GatheringNodeDefinition parseGathering(RawGatheringNodeDefinition raw) {
        if (raw.presentation() == null) {
            throw new IllegalArgumentException("gathering node requires presentation");
        }
        List<GatheringYieldDefinition> yields = raw.yields() == null ? List.of()
                : raw.yields().stream().map(yield -> {
                    if (yield.amount() == null || yield.amount().size() != 2) {
                        throw new IllegalArgumentException("gathering yield amount must be [min,max]");
                    }
                    return new GatheringYieldDefinition(ContentId.parse(yield.item()),
                            yield.amount().get(0), yield.amount().get(1),
                            yield.chance() == null ? 1.0 : yield.chance());
                }).toList();
        return new GatheringNodeDefinition(
                ContentId.parse(raw.id()), raw.displayName(), ContentId.parse(raw.skill()),
                GatheringNodeDefinition.Tier.valueOf(raw.tier().toUpperCase(Locale.ROOT)),
                raw.baseXp(), raw.requiredToolTag(), raw.requiredLevel(),
                raw.harvestTimeMillis(), Math.multiplyExact(raw.respawnSeconds(), 1000),
                Math.multiplyExact(raw.respawnJitterSeconds(), 1000),
                raw.tags() == null ? Set.of() : raw.tags(),
                new GatheringNodeDefinition.Presentation(
                        raw.presentation().availableBlock(),
                        raw.presentation().depletedBlock(), raw.presentation().hologram()),
                yields);
    }

    private LootDefinition parseLoot(RawLootDefinition raw) {
        List<LootEntry> entries = raw.entries() == null ? List.of() : raw.entries().stream()
                .map(entry -> new LootEntry(entry.id(), ContentId.parse(entry.itemId()),
                        entry.weight(), entry.guaranteed(), entry.minimumQuantity(),
                        entry.maximumQuantity(),
                        entry.requiredConditions() == null ? Set.of() : entry.requiredConditions(),
                        entry.pityAfter(), entry.perRollCap()))
                .toList();
        return new LootDefinition(ContentId.parse(raw.id()), raw.displayName(),
                LootDefinition.Ownership.valueOf(raw.ownership().toUpperCase(Locale.ROOT)),
                raw.weightedRolls(), raw.contributionRequired(), entries);
    }

    private MasteryDefinition parseMastery(RawMasteryDefinition raw) {
        return new MasteryDefinition(ContentId.parse(raw.id()), raw.displayName(),
                MasteryDefinition.Kind.valueOf(raw.kind().toUpperCase(Locale.ROOT)),
                raw.parent() == null || raw.parent().isBlank() ? null : ContentId.parse(raw.parent()),
                raw.maximumLevel(), raw.curveBase(), raw.curveExponent(),
                raw.maximumPowerBonus(), raw.treeRevision() < 1 ? 1 : raw.treeRevision());
    }

    private MasteryNodeDefinition parseMasteryNode(RawMasteryNodeDefinition raw) {
        ContentId id = ContentId.parse(raw.id());
        ModifierSource source = ModifierSource.of(ModifierSource.SourceType.MASTERY, id.toString());
        List<AttributeModifier> modifiers = raw.modifiers() == null ? List.of()
                : raw.modifiers().stream().map(modifier -> new AttributeModifier(
                        modifier.id(), AttributeType.fromContentKey(modifier.attribute()),
                        ModifierOperation.valueOf(modifier.operation().toUpperCase(Locale.ROOT)),
                        modifier.value(), source, modifier.stackingGroup(), modifier.priority(),
                        Optional.<java.time.Instant>empty())).toList();
        Map<ContentId, Integer> prerequisites = new LinkedHashMap<>();
        if (raw.prerequisites() != null) raw.prerequisites().forEach((required, rank) ->
                prerequisites.put(ContentId.parse(required), rank));
        return new MasteryNodeDefinition(id, ContentId.parse(raw.masteryId()), raw.treeRevision(),
                raw.branchId(), raw.maximumRank(), raw.pointCostPerRank(),
                raw.requiredMasteryLevel(), prerequisites, Optional.ofNullable(raw.exclusionGroup()),
                raw.unlockedSkill() == null || raw.unlockedSkill().isBlank()
                        ? Optional.empty() : Optional.of(ContentId.parse(raw.unlockedSkill())),
                modifiers);
    }

    private WeaponDefinition parseWeapon(RawWeaponDefinition raw) {
        return new WeaponDefinition(ContentId.parse(raw.id()), raw.displayName(),
                ContentId.parse(raw.familyMastery()), ContentId.parse(raw.typeMastery()),
                ContentId.parse(raw.basicAttackSkill()),
                raw.activeSkills() == null ? List.of()
                        : raw.activeSkills().stream().map(ContentId::parse).toList(),
                raw.tags() == null ? Set.of() : raw.tags(), raw.twoHanded(),
                raw.familyXpShare() == 0.0 && raw.typeXpShare() == 0.0
                        && raw.skillXpShare() == 0.0 ? 0.40 : raw.familyXpShare(),
                raw.familyXpShare() == 0.0 && raw.typeXpShare() == 0.0
                        && raw.skillXpShare() == 0.0 ? 0.60 : raw.typeXpShare(),
                raw.skillXpShare());
    }

    private LifeSkillDefinition parseLifeSkill(RawLifeSkillDefinition raw) {
        return new LifeSkillDefinition(ContentId.parse(raw.id()), raw.displayName(),
                raw.maximumLevel(), raw.curveBase(), raw.curveExponent(),
                raw.pointMilestones() == null ? java.util.Set.of() : raw.pointMilestones());
    }

    private LifeSkillNodeDefinition parseLifeSkillNode(RawLifeSkillNodeDefinition raw) {
        Map<ContentId, Integer> prerequisites = new LinkedHashMap<>();
        if (raw.prerequisites() != null) {
            raw.prerequisites().forEach((id, rank) ->
                    prerequisites.put(ContentId.parse(id), rank));
        }
        if (raw.effect() == null) {
            throw new IllegalArgumentException("life_skill_node requires an effect");
        }
        var effect = new LifeSkillNodeDefinition.Effect(raw.effect().type(),
                raw.effect().targetTags() == null ? java.util.Set.of() : raw.effect().targetTags(),
                raw.effect().percentPerRank(), raw.effect().capPercent());
        return new LifeSkillNodeDefinition(ContentId.parse(raw.id()), ContentId.parse(raw.skill()),
                raw.displayName(), raw.maximumRank(), raw.pointCostPerRank(),
                raw.requiredLevel(), prerequisites, effect);
    }

    private SkillDefinition parseSkill(RawSkillDefinition raw) {
        Map<ResourceType, Double> costs = new LinkedHashMap<>();
        if (raw.costs() != null) {
            raw.costs().forEach((key, value) ->
                    costs.put(ResourceType.valueOf(key.toUpperCase(Locale.ROOT)), value));
        }
        Map<String, SkillEffectNode> effects = new LinkedHashMap<>();
        if (raw.effects() != null) {
            for (RawSkillDefinition.RawEffectNode node : raw.effects()) {
                SkillEffectNode parsed = new SkillEffectNode(node.id(),
                        SkillEffectType.valueOf(node.type().toUpperCase(Locale.ROOT)),
                        node.numbers() == null ? Map.of() : node.numbers(),
                        node.values() == null ? Map.of() : node.values(),
                        node.children() == null ? List.of() : node.children());
                if (effects.putIfAbsent(parsed.id(), parsed) != null) {
                    throw new IllegalArgumentException("duplicate effect node " + parsed.id());
                }
            }
        }
        return new SkillDefinition(ContentId.parse(raw.id()), raw.displayName(), raw.inputSlot(),
                raw.tags() == null ? java.util.Set.of() : raw.tags(),
                raw.castMillis(), raw.activeMillis(), raw.recoveryMillis(), raw.cooldownMillis(),
                raw.cooldownGroup(), costs, raw.interruptRefund(), raw.range(),
                raw.requiresLineOfSight(), effects, raw.rootEffect());
    }

    private boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private String relative(Path root, Path file) {
        return root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
