package com.branz.mmorpg.api.character;

import com.branz.mmorpg.api.content.ContentId;
import java.util.Objects;

/** Typed stable identifier for a permanent character class. */
public record CharacterClassId(ContentId value) implements Comparable<CharacterClassId> {
    public static final CharacterClassId WARRIOR = parse("branz:warrior");
    public static final CharacterClassId MAGE = parse("branz:mage");
    public static final CharacterClassId ROGUE = parse("branz:rogue");

    public CharacterClassId {
        Objects.requireNonNull(value, "value");
    }

    public static CharacterClassId parse(String value) {
        return new CharacterClassId(ContentId.parse(value));
    }

    @Override public int compareTo(CharacterClassId other) {
        return value.compareTo(other.value);
    }

    @Override public String toString() {
        return value.toString();
    }
}
