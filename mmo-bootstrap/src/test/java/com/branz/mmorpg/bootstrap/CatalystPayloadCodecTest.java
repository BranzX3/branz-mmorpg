package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CatalystPayloadCodecTest {
    @Test
    void legacyPayloadStartsFullAndCommitPreservesUnrelatedFields() {
        CatalystDurability full = CatalystPayloadCodec.decode("{}", 100);
        String encoded =
                CatalystPayloadCodec.encode(
                        "{\"displayRevision\":4,\"testProvenance\":\"dev:test\"}", full.spend(1));

        assertEquals(new CatalystDurability(99, 100), CatalystPayloadCodec.decode(encoded, 100));
        assertTrue(encoded.contains("\"displayRevision\":5"));
        assertTrue(encoded.contains("\"testProvenance\":\"dev:test\""));
    }

    @Test
    void rejectsBrokenSpendMalformedStateAndDefinitionMismatch() {
        assertThrows(IllegalArgumentException.class, () -> new CatalystDurability(0, 100).spend(1));
        assertThrows(
                IllegalArgumentException.class,
                () -> CatalystPayloadCodec.decode("{\"catalyst\":[]}", 100));
        String encoded = CatalystPayloadCodec.encode("{}", new CatalystDurability(99, 100));
        assertThrows(
                IllegalArgumentException.class, () -> CatalystPayloadCodec.decode(encoded, 120));
    }
}
