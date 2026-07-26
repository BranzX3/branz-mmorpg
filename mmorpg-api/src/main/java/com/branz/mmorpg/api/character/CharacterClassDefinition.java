package com.branz.mmorpg.api.character;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import com.branz.mmorpg.api.skill.ResourceType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validated content definition for one permanent class. */
public record CharacterClassDefinition(
        ContentId id,
        String displayName,
        int schemaVersion,
        Set<CharacterClassRole> roles,
        Map<String, Double> baseAttributes,
        ResourceType primaryResource,
        Set<ResourceType> secondaryResources,
        Set<String> allowedWeaponTags,
        Set<String> allowedArmorTags,
        List<ContentId> classSkillIds,
        ContentId ultimateSkillId,
        ContentId passiveRootNodeId,
        StarterGrantPlan starterGrantPlan,
        Set<String> tags,
        int maximumLevel,
        double xpCurveBase,
        double xpCurveExponent,
        Set<Integer> bonusSkillPointLevels,
        int treeRevision) implements ContentDefinition {
    public CharacterClassDefinition {
        Objects.requireNonNull(id, "id");
        displayName = displayName == null || displayName.isBlank() ? id.toString() : displayName.trim();
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        baseAttributes = Map.copyOf(Objects.requireNonNull(baseAttributes, "baseAttributes"));
        Objects.requireNonNull(primaryResource, "primaryResource");
        secondaryResources = Set.copyOf(Objects.requireNonNull(secondaryResources, "secondaryResources"));
        allowedWeaponTags = Set.copyOf(Objects.requireNonNull(allowedWeaponTags, "allowedWeaponTags"));
        allowedArmorTags = Set.copyOf(Objects.requireNonNull(allowedArmorTags, "allowedArmorTags"));
        classSkillIds = List.copyOf(Objects.requireNonNull(classSkillIds, "classSkillIds"));
        Objects.requireNonNull(ultimateSkillId, "ultimateSkillId");
        Objects.requireNonNull(passiveRootNodeId, "passiveRootNodeId");
        Objects.requireNonNull(starterGrantPlan, "starterGrantPlan");
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
        bonusSkillPointLevels = Set.copyOf(Objects.requireNonNull(
                bonusSkillPointLevels, "bonusSkillPointLevels"));
        if (schemaVersion < 1) throw new IllegalArgumentException("class schema version must be positive");
        if (roles.isEmpty() || allowedWeaponTags.isEmpty() || classSkillIds.isEmpty()) {
            throw new IllegalArgumentException("class roles, weapon tags, and skills must not be empty");
        }
        if (primaryResource == ResourceType.HEALTH || secondaryResources.contains(ResourceType.HEALTH)
                || secondaryResources.contains(primaryResource)) {
            throw new IllegalArgumentException("class combat resources are invalid");
        }
        baseAttributes.forEach((attribute, value) -> {
            if (attribute == null || attribute.isBlank() || value == null || !Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("invalid base attribute " + attribute);
            }
        });
        if (maximumLevel < 1 || maximumLevel > 10_000
                || !Double.isFinite(xpCurveBase) || xpCurveBase <= 0
                || !Double.isFinite(xpCurveExponent) || xpCurveExponent <= 0
                || treeRevision < 1) {
            throw new IllegalArgumentException("class progression curve is invalid");
        }
        bonusSkillPointLevels.forEach(level -> {
            if (level == null || level < 2 || level > maximumLevel) {
                throw new IllegalArgumentException("invalid bonus Skill Point level " + level);
            }
        });
    }

    /** Compatibility constructor for code-defined fixtures created before K2. */
    public CharacterClassDefinition(
            ContentId id, String displayName, int schemaVersion,
            Set<CharacterClassRole> roles, Map<String, Double> baseAttributes,
            ResourceType primaryResource, Set<ResourceType> secondaryResources,
            Set<String> allowedWeaponTags, Set<String> allowedArmorTags,
            List<ContentId> classSkillIds, ContentId ultimateSkillId,
            ContentId passiveRootNodeId, StarterGrantPlan starterGrantPlan, Set<String> tags) {
        this(id, displayName, schemaVersion, roles, baseAttributes, primaryResource,
                secondaryResources, allowedWeaponTags, allowedArmorTags, classSkillIds,
                ultimateSkillId, passiveRootNodeId, starterGrantPlan, tags,
                100, 100.0, 1.65, Set.of(), 1);
    }

    public CharacterClassId classId() {
        return new CharacterClassId(id);
    }

    @Override public ContentType type() {
        return ContentType.CHARACTER_CLASS;
    }
}
