package com.branz.mmorpg.api.gathering;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record GatheringNodeDefinition(
        ContentId id,
        String displayName,
        ContentId skillId,
        Tier tier,
        long baseXp,
        String requiredToolTag,
        int requiredLevel,
        long harvestTimeMillis,
        long respawnMillis,
        long respawnJitterMillis,
        Set<String> tags,
        Presentation presentation,
        List<GatheringYieldDefinition> yields) implements ContentDefinition {

    public enum Tier { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }

    public GatheringNodeDefinition {
        Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(tier, "tier");
        requiredToolTag = Objects.requireNonNull(requiredToolTag, "requiredToolTag").trim();
        tags = Set.copyOf(tags);
        Objects.requireNonNull(presentation, "presentation");
        yields = List.copyOf(yields);
        if (displayName.isEmpty() || baseXp < 0 || requiredToolTag.isEmpty()
                || requiredLevel < 1 || harvestTimeMillis < 1 || respawnMillis < 1
                || respawnJitterMillis < 0 || respawnJitterMillis > respawnMillis
                || yields.isEmpty()) {
            throw new IllegalArgumentException("invalid gathering node definition " + id);
        }
    }

    @Override public ContentType type() { return ContentType.GATHERING_NODE; }

    public record Presentation(
            String availableBlock, String depletedBlock, String hologram) {
        public Presentation {
            availableBlock = require(availableBlock, "availableBlock");
            depletedBlock = require(depletedBlock, "depletedBlock");
            hologram = require(hologram, "hologram");
        }

        private static String require(String value, String name) {
            String result = Objects.requireNonNull(value, name).trim();
            if (result.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
            return result;
        }
    }
}
