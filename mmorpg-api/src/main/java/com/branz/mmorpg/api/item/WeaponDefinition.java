package com.branz.mmorpg.api.item;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Weapon hierarchy and equipped skill slots for the initial one-weapon loadout. */
public record WeaponDefinition(
        ContentId id,
        String displayName,
        ContentId familyMasteryId,
        ContentId typeMasteryId,
        ContentId basicAttackSkillId,
        List<ContentId> activeSkillIds,
        Set<String> tags,
        boolean twoHanded) implements ContentDefinition {

    public WeaponDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(familyMasteryId, "familyMasteryId");
        Objects.requireNonNull(typeMasteryId, "typeMasteryId");
        Objects.requireNonNull(basicAttackSkillId, "basicAttackSkillId");
        Objects.requireNonNull(activeSkillIds, "activeSkillIds");
        Objects.requireNonNull(tags, "tags");
        displayName = displayName == null || displayName.isBlank() ? id.value() : displayName.trim();
        activeSkillIds = List.copyOf(activeSkillIds);
        tags = Set.copyOf(tags);
    }

    @Override
    public ContentType type() {
        return ContentType.WEAPON;
    }
}
