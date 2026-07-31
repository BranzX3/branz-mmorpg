package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.combat.crossbow.CrossbowPersistentState;
import org.junit.jupiter.api.Test;

class CrossbowPayloadCodecTest {
    private static final DefinitionId BOLT = DefinitionId.of("ammo.test.bolt");

    @Test
    void preservesUnrelatedFieldsAndAdvancesDisplayRevision() {
        String encoded =
                CrossbowPayloadCodec.encode(
                        "{\"displayRevision\":4,\"testProvenance\":\"dev:test\"}",
                        CrossbowPersistentState.boltPlaced(BOLT));

        assertEquals(
                CrossbowPersistentState.boltPlaced(BOLT), CrossbowPayloadCodec.decode(encoded));
        assertTrue(encoded.contains("\"displayRevision\":5"));
        assertTrue(encoded.contains("\"testProvenance\":\"dev:test\""));
    }

    @Test
    void legacyPayloadIsUnloadedAndEveryDurableCheckpointRoundTrips() {
        assertEquals(CrossbowPersistentState.unloaded(), CrossbowPayloadCodec.decode("{}"));
        String loaded = CrossbowPayloadCodec.encode("{}", CrossbowPersistentState.loaded(BOLT));
        String unloaded = CrossbowPayloadCodec.encode(loaded, CrossbowPersistentState.unloaded());

        assertEquals(CrossbowPersistentState.loaded(BOLT), CrossbowPayloadCodec.decode(loaded));
        assertEquals(CrossbowPersistentState.unloaded(), CrossbowPayloadCodec.decode(unloaded));
    }

    @Test
    void rejectsUnknownOrAmmoLessDurableCheckpoint() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CrossbowPayloadCodec.decode("{\"crossbow\":{\"checkpoint\":\"LOADED\"}}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> CrossbowPayloadCodec.decode("{\"crossbow\":{\"checkpoint\":\"UNKNOWN\"}}"));
        assertThrows(IllegalArgumentException.class, () -> CrossbowPayloadCodec.decode("[]"));
    }
}
