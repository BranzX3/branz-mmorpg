package com.branz.mmorpg.api.build;

import com.branz.mmorpg.api.character.CharacterClassRole;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.input.SkillSlot;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable server-derived view of the active class and weapon build. */
public record BuildSnapshot(
        UUID playerId,
        long buildRevision,
        long contentRevision,
        ContentId classId,
        ContentId weaponId,
        ContentId familyMasteryId,
        ContentId typeMasteryId,
        String title,
        Map<SkillSlot, ContentId> skillBindings,
        Set<ContentId> unlockedSkillIds,
        Map<ContentId, Integer> masteryLevels,
        Map<CharacterClassRole, Double> roleWeights,
        Instant generatedAt) {
    public BuildSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(classId, "classId");
        Objects.requireNonNull(weaponId, "weaponId");
        Objects.requireNonNull(familyMasteryId, "familyMasteryId");
        Objects.requireNonNull(typeMasteryId, "typeMasteryId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(generatedAt, "generatedAt");
        skillBindings = Map.copyOf(Objects.requireNonNull(skillBindings, "skillBindings"));
        unlockedSkillIds = Set.copyOf(Objects.requireNonNull(unlockedSkillIds, "unlockedSkillIds"));
        masteryLevels = Map.copyOf(Objects.requireNonNull(masteryLevels, "masteryLevels"));
        roleWeights = Map.copyOf(Objects.requireNonNull(roleWeights, "roleWeights"));
        if (buildRevision < 0 || contentRevision < 1 || title.isBlank()) {
            throw new IllegalArgumentException("invalid build snapshot");
        }
    }
}
