package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItemDurabilityPayloadCodecTest {
    @Test
    void preservesGenericPayloadAndAdvancesDisplayRevision() {
        String original = "{\"displayRevision\":7,\"testProvenance\":\"dev:test\"}";

        String encoded = ItemDurabilityPayloadCodec.encode(original, new ItemDurability(4, 5));

        assertEquals(4, ItemDurabilityPayloadCodec.decode(encoded, 5).current());
        assertTrue(encoded.contains("\"displayRevision\":8"));
        assertTrue(encoded.contains("\"testProvenance\":\"dev:test\""));
    }

    @Test
    void missingDurabilityDefaultsToAuthoredMaximum() {
        ItemDurability durability = ItemDurabilityPayloadCodec.decode("{}", 180);

        assertEquals(new ItemDurability(180, 180), durability);
    }
}
