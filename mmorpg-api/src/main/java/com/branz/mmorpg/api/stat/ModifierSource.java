package com.branz.mmorpg.api.stat;

import java.util.Objects;

/**
 * Where a modifier came from.
 *
 * <p>Kept on every modifier so a bulk removal — unequipping an item, cleansing a
 * status, respeccing a tree — can drop exactly that source's contributions
 * without knowing their individual IDs, and so admin tooling can explain why a
 * player's numbers look the way they do.
 *
 * @param type what kind of thing granted this
 * @param id   stable identifier of the granting thing, e.g. an item instance
 *             UUID or a status content ID
 */
public record ModifierSource(SourceType type, String id) {

    public enum SourceType {
        EQUIPMENT,
        SKILL,
        STATUS,
        MASTERY,
        LIFE_SKILL,
        CONSUMABLE,
        ADMIN
    }

    public ModifierSource {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("source id must not be blank");
        }
    }

    public static ModifierSource of(SourceType type, String id) {
        return new ModifierSource(type, id);
    }

    @Override
    public String toString() {
        return type + ":" + id;
    }
}
