package com.branz.mmorpg.api.item;

import com.branz.mmorpg.api.content.ContentId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Authoritative unique equipment/item identity; Bukkit ItemStack is only a token. */
public record ItemInstance(
        UUID instanceId,
        ContentId definitionId,
        ItemCategory category,
        long qualitySeed,
        Optional<UUID> boundOwner,
        int durability,
        String createdSource,
        int schemaVersion,
        Instant createdAt) {

    public ItemInstance {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(boundOwner, "boundOwner");
        Objects.requireNonNull(createdAt, "createdAt");
        createdSource = Objects.requireNonNull(createdSource, "createdSource").trim();
        if (category == ItemCategory.MATERIAL || durability < 0
                || schemaVersion < 1 || createdSource.isEmpty()) {
            throw new IllegalArgumentException("invalid unique item instance");
        }
    }
}
