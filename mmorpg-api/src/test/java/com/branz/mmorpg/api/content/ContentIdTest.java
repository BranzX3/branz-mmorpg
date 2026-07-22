package com.branz.mmorpg.api.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ContentIdTest {
    @Test
    void parsesAndNormalizesNamespacedIds() {
        assertEquals("branz:weapon/broadsword", ContentId.parse(" Branz:Weapon/Broadsword ").toString());
    }

    @Test
    void rejectsMissingNamespace() {
        assertThrows(IllegalArgumentException.class, () -> ContentId.parse("broadsword"));
    }

    @Test
    void rejectsUnsafeCharacters() {
        assertThrows(IllegalArgumentException.class, () -> ContentId.parse("branz:../secret"));
        assertThrows(IllegalArgumentException.class, () -> ContentId.parse("branz:items/../secret"));
        assertThrows(IllegalArgumentException.class, () -> ContentId.parse("branz:items//secret"));
        assertThrows(IllegalArgumentException.class, () -> ContentId.parse("branz:aether ore"));
    }
}
