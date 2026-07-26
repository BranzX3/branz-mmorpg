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
        boolean twoHanded,
        double familyXpShare,
        double typeXpShare,
        double skillXpShare) implements ContentDefinition {

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
        if (!validShare(familyXpShare) || !validShare(typeXpShare) || !validShare(skillXpShare)
                || Math.abs(familyXpShare + typeXpShare + skillXpShare - 1.0) > 1e-9) {
            throw new IllegalArgumentException(id + ": mastery XP shares must total 1.0");
        }
    }

    public WeaponDefinition(ContentId id, String displayName, ContentId familyMasteryId,
                            ContentId typeMasteryId, ContentId basicAttackSkillId,
                            List<ContentId> activeSkillIds, Set<String> tags, boolean twoHanded) {
        this(id, displayName, familyMasteryId, typeMasteryId, basicAttackSkillId,
                activeSkillIds, tags, twoHanded, 0.40, 0.60, 0.0);
    }

    @Override
    public ContentType type() {
        return ContentType.WEAPON;
    }

    private static boolean validShare(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }
}
