package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;

record RawWeaponDefinition(
        String type,
        String id,
        @JsonProperty("display-name") String displayName,
        @JsonProperty("family-mastery") String familyMastery,
        @JsonProperty("type-mastery") String typeMastery,
        @JsonProperty("basic-attack-skill") String basicAttackSkill,
        @JsonProperty("active-skills") List<String> activeSkills,
        Set<String> tags,
        @JsonProperty("two-handed") boolean twoHanded,
        @JsonProperty("family-xp-share") double familyXpShare,
        @JsonProperty("type-xp-share") double typeXpShare,
        @JsonProperty("skill-xp-share") double skillXpShare) {
}
