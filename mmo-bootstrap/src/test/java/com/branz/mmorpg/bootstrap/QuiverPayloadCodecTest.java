package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.items.quiver.QuiverPreparation;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuiverPayloadCodecTest {
    @Test
    void roundTripAdvancesDisplayRevisionAndPreservesUnrelatedPayload() {
        QuiverPreparation preparation =
                new QuiverPreparation(
                        List.of(
                                DefinitionId.of("ammo.test.basic"),
                                DefinitionId.of("ammo.test.bodkin")),
                        1);

        String encoded =
                QuiverPayloadCodec.encode(
                        "{\"displayRevision\":7,\"testProvenance\":\"dev:test\",\"roll\":3}",
                        preparation);

        assertEquals(preparation, QuiverPayloadCodec.decode(encoded));
        assertTrue(encoded.contains("\"displayRevision\":8"));
        assertTrue(encoded.contains("\"testProvenance\":\"dev:test\""));
        assertTrue(encoded.contains("\"roll\":3"));
    }

    @Test
    void missingStateIsEmptyAndMalformedStateFailsClosed() {
        assertEquals(QuiverPreparation.empty(), QuiverPayloadCodec.decode("{}"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        QuiverPayloadCodec.decode(
                                "{\"quiver\":{\"preparedAmmo\":[],\"selectedIndex\":0}}"));
        assertThrows(IllegalArgumentException.class, () -> QuiverPayloadCodec.decode("[]"));
    }
}
