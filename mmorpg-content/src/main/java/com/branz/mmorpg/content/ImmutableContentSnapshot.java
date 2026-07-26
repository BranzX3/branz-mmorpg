package com.branz.mmorpg.content;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.mastery.MasteryNodeDefinition;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.item.LootDefinition;
import com.branz.mmorpg.api.gathering.GatheringNodeDefinition;
import com.branz.mmorpg.api.crafting.ProfessionDefinition;
import com.branz.mmorpg.api.crafting.RecipeDefinition;
import com.branz.mmorpg.api.mob.MobDefinition;
import com.branz.mmorpg.api.encounter.EncounterDefinition;
import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.character.ClassSkillNodeDefinition;
import com.branz.mmorpg.api.status.StatusDefinition;
import com.branz.mmorpg.api.input.CombatInputProfileDefinition;
import com.branz.mmorpg.api.input.CombatComboDefinition;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class ImmutableContentSnapshot implements ContentSnapshot {
    private final long revision;
    private final Instant loadedAt;
    private final Map<ContentId, ContentDefinition> definitions;
    private final Map<ContentId, MaterialDefinition> materials;
    private final Map<ContentId, SkillDefinition> skills;
    private final Map<ContentId, LifeSkillDefinition> lifeSkills;
    private final Map<ContentId, LifeSkillNodeDefinition> lifeSkillNodes;
    private final Map<ContentId, MasteryDefinition> masteries;
    private final Map<ContentId, MasteryNodeDefinition> masteryNodes;
    private final Map<ContentId, WeaponDefinition> weapons;
    private final Map<ContentId, LootDefinition> lootTables;
    private final Map<ContentId, GatheringNodeDefinition> gatheringNodes;
    private final Map<ContentId, ProfessionDefinition> professions;
    private final Map<ContentId, RecipeDefinition> recipes;
    private final Map<ContentId, MobDefinition> mobs;
    private final Map<ContentId, EncounterDefinition> encounters;
    private final Map<ContentId, CharacterClassDefinition> characterClasses;
    private final Map<ContentId, StatusDefinition> statuses;
    private final Map<ContentId, ClassSkillNodeDefinition> classSkillNodes;
    private final Map<ContentId, CombatInputProfileDefinition> combatInputProfiles;
    private final Map<ContentId, CombatComboDefinition> combatCombos;

    ImmutableContentSnapshot(long revision, Instant loadedAt, Map<ContentId, ContentDefinition> definitions) {
        this.revision = revision;
        this.loadedAt = loadedAt;
        this.definitions = Map.copyOf(definitions);
        Map<ContentId, MaterialDefinition> materialIndex = new LinkedHashMap<>();
        definitions.forEach((id, definition) -> {
            if (definition instanceof MaterialDefinition material) {
                materialIndex.put(id, material);
            }
        });
        this.materials = Map.copyOf(materialIndex);
        Map<ContentId, SkillDefinition> skillIndex = new LinkedHashMap<>();
        definitions.forEach((id, definition) -> {
            if (definition instanceof SkillDefinition skill) {
                skillIndex.put(id, skill);
            }
        });
        this.skills = Map.copyOf(skillIndex);
        Map<ContentId, LifeSkillDefinition> lifeSkillIndex = new LinkedHashMap<>();
        Map<ContentId, LifeSkillNodeDefinition> nodeIndex = new LinkedHashMap<>();
        definitions.forEach((id, definition) -> {
            if (definition instanceof LifeSkillDefinition lifeSkill) {
                lifeSkillIndex.put(id, lifeSkill);
            } else if (definition instanceof LifeSkillNodeDefinition node) {
                nodeIndex.put(id, node);
            }
        });
        this.lifeSkills = Map.copyOf(lifeSkillIndex);
        this.lifeSkillNodes = Map.copyOf(nodeIndex);
        Map<ContentId, MasteryDefinition> masteryIndex = new LinkedHashMap<>();
        Map<ContentId, MasteryNodeDefinition> masteryNodeIndex = new LinkedHashMap<>();
        Map<ContentId, WeaponDefinition> weaponIndex = new LinkedHashMap<>();
        Map<ContentId, LootDefinition> lootIndex = new LinkedHashMap<>();
        Map<ContentId, GatheringNodeDefinition> gatheringIndex = new LinkedHashMap<>();
        Map<ContentId, ProfessionDefinition> professionIndex = new LinkedHashMap<>();
        Map<ContentId, RecipeDefinition> recipeIndex = new LinkedHashMap<>();
        Map<ContentId, MobDefinition> mobIndex = new LinkedHashMap<>();
        Map<ContentId, EncounterDefinition> encounterIndex = new LinkedHashMap<>();
        Map<ContentId, CharacterClassDefinition> classIndex = new LinkedHashMap<>();
        Map<ContentId, StatusDefinition> statusIndex = new LinkedHashMap<>();
        Map<ContentId, ClassSkillNodeDefinition> classNodeIndex = new LinkedHashMap<>();
        Map<ContentId, CombatInputProfileDefinition> inputProfileIndex = new LinkedHashMap<>();
        Map<ContentId, CombatComboDefinition> comboIndex = new LinkedHashMap<>();
        definitions.forEach((id, definition) -> {
            if (definition instanceof MasteryDefinition mastery) {
                masteryIndex.put(id, mastery);
            } else if (definition instanceof MasteryNodeDefinition masteryNode) {
                masteryNodeIndex.put(id, masteryNode);
            } else if (definition instanceof WeaponDefinition weapon) {
                weaponIndex.put(id, weapon);
            } else if (definition instanceof LootDefinition loot) {
                lootIndex.put(id, loot);
            } else if (definition instanceof GatheringNodeDefinition gathering) {
                gatheringIndex.put(id, gathering);
            } else if (definition instanceof ProfessionDefinition profession) {
                professionIndex.put(id, profession);
            } else if (definition instanceof RecipeDefinition recipe) {
                recipeIndex.put(id, recipe);
            } else if (definition instanceof MobDefinition mob) {
                mobIndex.put(id, mob);
            } else if (definition instanceof EncounterDefinition encounter) {
                encounterIndex.put(id, encounter);
            } else if (definition instanceof CharacterClassDefinition characterClass) {
                classIndex.put(id, characterClass);
            } else if (definition instanceof StatusDefinition status) {
                statusIndex.put(id, status);
            } else if (definition instanceof ClassSkillNodeDefinition node) {
                classNodeIndex.put(id, node);
            } else if (definition instanceof CombatInputProfileDefinition profile) {
                inputProfileIndex.put(id, profile);
            } else if (definition instanceof CombatComboDefinition combo) {
                comboIndex.put(id, combo);
            }
        });
        this.masteries = Map.copyOf(masteryIndex);
        this.masteryNodes = Map.copyOf(masteryNodeIndex);
        this.weapons = Map.copyOf(weaponIndex);
        this.lootTables = Map.copyOf(lootIndex);
        this.gatheringNodes = Map.copyOf(gatheringIndex);
        this.professions = Map.copyOf(professionIndex);
        this.recipes = Map.copyOf(recipeIndex);
        this.mobs = Map.copyOf(mobIndex);
        this.encounters = Map.copyOf(encounterIndex);
        this.characterClasses = Map.copyOf(classIndex);
        this.statuses = Map.copyOf(statusIndex);
        this.classSkillNodes = Map.copyOf(classNodeIndex);
        this.combatInputProfiles = Map.copyOf(inputProfileIndex);
        this.combatCombos = Map.copyOf(comboIndex);
    }

    static ImmutableContentSnapshot empty() {
        return new ImmutableContentSnapshot(0, Instant.EPOCH, Map.of());
    }

    @Override
    public long revision() {
        return revision;
    }

    @Override
    public Instant loadedAt() {
        return loadedAt;
    }

    @Override
    public Collection<ContentDefinition> definitions() {
        return definitions.values();
    }

    @Override
    public Optional<ContentDefinition> find(ContentId id) {
        return Optional.ofNullable(definitions.get(id));
    }

    @Override
    public <T extends ContentDefinition> Optional<T> find(ContentId id, Class<T> type) {
        return find(id).filter(type::isInstance).map(type::cast);
    }

    @Override
    public Map<ContentId, MaterialDefinition> materials() {
        return materials;
    }

    @Override
    public Map<ContentId, SkillDefinition> skills() {
        return skills;
    }

    @Override
    public Map<ContentId, LifeSkillDefinition> lifeSkills() {
        return lifeSkills;
    }

    @Override
    public Map<ContentId, LifeSkillNodeDefinition> lifeSkillNodes() {
        return lifeSkillNodes;
    }

    @Override
    public Map<ContentId, MasteryDefinition> masteries() {
        return masteries;
    }

    @Override public Map<ContentId, MasteryNodeDefinition> masteryNodes() {
        return masteryNodes;
    }

    @Override
    public Map<ContentId, WeaponDefinition> weapons() {
        return weapons;
    }

    @Override
    public Map<ContentId, LootDefinition> lootTables() {
        return lootTables;
    }

    @Override
    public Map<ContentId, GatheringNodeDefinition> gatheringNodes() {
        return gatheringNodes;
    }

    @Override public Map<ContentId, ProfessionDefinition> professions() {
        return professions;
    }

    @Override public Map<ContentId, RecipeDefinition> recipes() {
        return recipes;
    }

    @Override public Map<ContentId, MobDefinition> mobs() {
        return mobs;
    }

    @Override public Map<ContentId, EncounterDefinition> encounters() {
        return encounters;
    }

    @Override public Map<ContentId, CharacterClassDefinition> characterClasses() {
        return characterClasses;
    }

    @Override public Map<ContentId, StatusDefinition> statuses() {
        return statuses;
    }

    @Override public Map<ContentId, ClassSkillNodeDefinition> classSkillNodes() {
        return classSkillNodes;
    }

    @Override public Map<ContentId, CombatInputProfileDefinition> combatInputProfiles() {
        return combatInputProfiles;
    }

    @Override public Map<ContentId, CombatComboDefinition> combatCombos() {
        return combatCombos;
    }
}
