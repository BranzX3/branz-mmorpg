package com.branz.mmorpg.api.character;

import com.branz.mmorpg.api.content.ContentId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, revisioned plan consumed by the I7 inventory delivery adapter. */
public record StarterGrantPlan(
        ContentId id,
        int revision,
        ContentId weaponId,
        List<ContentId> unlockedSkillIds,
        Map<ContentId, Integer> additionalItems) {
    public StarterGrantPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(weaponId, "weaponId");
        unlockedSkillIds = List.copyOf(Objects.requireNonNull(unlockedSkillIds, "unlockedSkillIds"));
        additionalItems = Map.copyOf(Objects.requireNonNull(additionalItems, "additionalItems"));
        if (revision < 1) throw new IllegalArgumentException("starter plan revision must be positive");
        if (unlockedSkillIds.isEmpty()) throw new IllegalArgumentException("starter plan needs a skill");
        additionalItems.forEach((item, quantity) -> {
            if (quantity == null || quantity < 1) {
                throw new IllegalArgumentException("starter item quantity must be positive: " + item);
            }
        });
    }
}
