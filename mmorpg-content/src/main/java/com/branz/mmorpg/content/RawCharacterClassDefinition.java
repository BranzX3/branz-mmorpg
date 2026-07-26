package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Set;

record RawCharacterClassDefinition(
        String type,
        String id,
        @JsonProperty("display-name") String displayName,
        @JsonProperty("schema-version") int schemaVersion,
        Set<String> roles,
        @JsonProperty("base-attributes") Map<String, Double> baseAttributes,
        @JsonProperty("primary-resource") String primaryResource,
        @JsonProperty("secondary-resources") Set<String> secondaryResources,
        @JsonProperty("allowed-weapon-tags") Set<String> allowedWeaponTags,
        @JsonProperty("allowed-armor-tags") Set<String> allowedArmorTags,
        @JsonProperty("class-skills") List<String> classSkills,
        @JsonProperty("ultimate-skill") String ultimateSkill,
        @JsonProperty("passive-root-node") String passiveRootNode,
        @JsonProperty("starter-grant") RawStarterGrant starterGrant,
        Set<String> tags) {
    record RawStarterGrant(
            String id,
            int revision,
            String weapon,
            @JsonProperty("unlocked-skills") List<String> unlockedSkills,
            @JsonProperty("additional-items") Map<String, Integer> additionalItems) {}
}
