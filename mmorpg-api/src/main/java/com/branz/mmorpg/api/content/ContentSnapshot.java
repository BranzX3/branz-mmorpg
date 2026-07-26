package com.branz.mmorpg.api.content;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
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

public interface ContentSnapshot {
    long revision();

    Instant loadedAt();

    Collection<ContentDefinition> definitions();

    Optional<ContentDefinition> find(ContentId id);

    <T extends ContentDefinition> Optional<T> find(ContentId id, Class<T> type);

    Map<ContentId, MaterialDefinition> materials();

    Map<ContentId, SkillDefinition> skills();

    Map<ContentId, LifeSkillDefinition> lifeSkills();

    Map<ContentId, LifeSkillNodeDefinition> lifeSkillNodes();

    Map<ContentId, MasteryDefinition> masteries();

    default Map<ContentId, MasteryNodeDefinition> masteryNodes() { return Map.of(); }

    Map<ContentId, WeaponDefinition> weapons();

    default Map<ContentId, LootDefinition> lootTables() {
        return Map.of();
    }

    default Map<ContentId, GatheringNodeDefinition> gatheringNodes() {
        return Map.of();
    }

    default Map<ContentId, ProfessionDefinition> professions() {
        return Map.of();
    }

    default Map<ContentId, RecipeDefinition> recipes() {
        return Map.of();
    }

    default Map<ContentId, MobDefinition> mobs() {
        return Map.of();
    }

    default Map<ContentId, EncounterDefinition> encounters() {
        return Map.of();
    }

    default Map<ContentId, CharacterClassDefinition> characterClasses() {
        return Map.of();
    }

    default Map<ContentId, StatusDefinition> statuses() {
        return Map.of();
    }

    default Map<ContentId, ClassSkillNodeDefinition> classSkillNodes() {
        return Map.of();
    }

    default Map<ContentId, CombatInputProfileDefinition> combatInputProfiles() {
        return Map.of();
    }

    default Map<ContentId, CombatComboDefinition> combatCombos() {
        return Map.of();
    }
}
