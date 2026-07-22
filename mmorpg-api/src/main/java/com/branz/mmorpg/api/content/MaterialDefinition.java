package com.branz.mmorpg.api.content;

import java.util.Objects;

public record MaterialDefinition(
        ContentId id,
        String displayName,
        String category,
        String rarity,
        boolean tradable,
        int maxStackSize
) implements ContentDefinition {
    public MaterialDefinition {
        Objects.requireNonNull(id, "id");
        displayName = requireText(displayName, "displayName");
        category = requireText(category, "category");
        rarity = requireText(rarity, "rarity");
        if (maxStackSize < 1 || maxStackSize > 99) {
            throw new IllegalArgumentException("maxStackSize must be between 1 and 99");
        }
    }

    @Override
    public ContentType type() {
        return ContentType.MATERIAL;
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return trimmed;
    }
}
