package com.branz.mmorpg.api.item;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record LootDefinition(
        ContentId id,
        String displayName,
        Ownership ownership,
        int weightedRolls,
        boolean contributionRequired,
        List<LootEntry> entries) implements ContentDefinition {

    public enum Ownership { PERSONAL, PARTY }

    public LootDefinition {
        Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        Objects.requireNonNull(ownership, "ownership");
        entries = List.copyOf(entries);
        if (displayName.isEmpty() || weightedRolls < 0 || entries.isEmpty()) {
            throw new IllegalArgumentException("invalid loot table");
        }
        var ids = new HashSet<String>();
        if (entries.stream().anyMatch(entry -> !ids.add(entry.entryId()))) {
            throw new IllegalArgumentException("duplicate loot entry ID");
        }
    }

    @Override public ContentType type() {
        return ContentType.LOOT_TABLE;
    }
}
