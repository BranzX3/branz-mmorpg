package com.branz.mmorpg.api.input;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Declarative logical key-to-skill-slot mapping with bounded timings. */
public record CombatInputProfileDefinition(
        ContentId id,
        long revision,
        Map<CombatInputKey, SkillSlot> bindings,
        long comboWindowMillis,
        long inputBufferMillis) implements ContentDefinition {

    public CombatInputProfileDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bindings, "bindings");
        bindings = Map.copyOf(new EnumMap<>(bindings));
        if (revision < 1 || bindings.size() != CombatInputKey.values().length
                || comboWindowMillis < 1 || comboWindowMillis > 2_000
                || inputBufferMillis < 0 || inputBufferMillis > 500) {
            throw new IllegalArgumentException(id + ": invalid combat input profile");
        }
    }

    @Override public ContentType type() { return ContentType.COMBAT_INPUT_PROFILE; }
}
