package com.branz.mmorpg.api.content;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillDefinition;
import com.branz.mmorpg.api.lifeskill.LifeSkillNodeDefinition;
import com.branz.mmorpg.api.mastery.MasteryDefinition;
import com.branz.mmorpg.api.item.WeaponDefinition;
import com.branz.mmorpg.api.item.LootDefinition;
import com.branz.mmorpg.api.gathering.GatheringNodeDefinition;
import com.branz.mmorpg.api.crafting.ProfessionDefinition;
import com.branz.mmorpg.api.crafting.RecipeDefinition;
import com.branz.mmorpg.api.mob.MobDefinition;
import com.branz.mmorpg.api.encounter.EncounterDefinition;

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
}
