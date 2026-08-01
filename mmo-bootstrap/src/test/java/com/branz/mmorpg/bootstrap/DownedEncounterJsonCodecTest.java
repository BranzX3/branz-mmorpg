package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.social.downed.DownedEncounterEngine;
import com.branz.mmorpg.social.downed.DownedEncounterRuntime;
import com.branz.mmorpg.social.downed.DownedErrorCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DownedEncounterJsonCodecTest {
    private final DownedEncounterEngine engine = new DownedEncounterEngine();
    private final DownedEncounterJsonCodec codec = new DownedEncounterJsonCodec();

    @Test
    void roundTripsAndRebasesMixedRuntimeCanonically() {
        CharacterId first = new CharacterId(UUID.randomUUID());
        CharacterId second = new CharacterId(UUID.randomUUID());
        CharacterId third = new CharacterId(UUID.randomUUID());
        DownedEncounterRuntime runtime =
                successful(
                        engine.start(
                                new EncounterId(UUID.randomUUID()), List.of(first, second, third)));
        runtime =
                successful(engine.lethalDamage(runtime, first, false, UUID.randomUUID(), 100))
                        .runtime();
        runtime =
                successful(
                                engine.beginRevive(
                                        runtime,
                                        third,
                                        first,
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        101))
                        .runtime();
        runtime = successful(engine.advance(runtime, UUID.randomUUID(), 181)).runtime();
        runtime =
                successful(engine.lethalDamage(runtime, second, false, UUID.randomUUID(), 190))
                        .runtime();
        runtime =
                successful(
                                engine.beginRevive(
                                        runtime,
                                        third,
                                        second,
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        195))
                        .runtime();

        String encoded = codec.encode(runtime, 200);
        DecodedDownedEncounter decoded = codec.decode(encoded);

        assertEquals(runtime, decoded.runtime());
        assertEquals(200, decoded.recordedAtTick());
        assertEquals(encoded, codec.encode(decoded.runtime(), decoded.recordedAtTick()));

        DecodedDownedEncounter rebased = codec.rebase(decoded, 10);
        assertEquals(10, rebased.recordedAtTick());
        assertEquals(300, rebased.runtime().participants().get(second).downedDeadlineTick());
        assertEquals(51, rebased.runtime().participants().get(first).protectionUntilTick());
        assertEquals(85, rebased.runtime().reviveChannelsByTarget().get(second).commitTick());
    }

    @Test
    void rejectsUnknownSchemaAndInvariantViolations() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("{\"schemaVersion\":2}"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        codec.decode(
                                "{\"schemaVersion\":1,\"encounterId\":\""
                                        + UUID.randomUUID()
                                        + "\",\"recordedAtTick\":0,\"participants\":[],"
                                        + "\"reviveChannels\":[],\"processedOperations\":[]}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.rebase(new DecodedDownedEncounter(oneParticipant(), 10), -1));
    }

    private DownedEncounterRuntime oneParticipant() {
        return successful(
                engine.start(
                        new EncounterId(UUID.randomUUID()),
                        List.of(new CharacterId(UUID.randomUUID()))));
    }

    private static <T> T successful(Result<T, DownedErrorCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<T, DownedErrorCode>) result).value();
    }
}
