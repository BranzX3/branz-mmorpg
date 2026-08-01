package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.worldloop.encounter.BossEncounterEngine;
import com.branz.mmorpg.worldloop.encounter.BossEncounterErrorCode;
import com.branz.mmorpg.worldloop.encounter.BossEncounterRuntime;
import com.branz.mmorpg.worldloop.reward.RewardContribution;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BossEncounterJsonCodecTest {
    private final BossEncounterEngine engine = new BossEncounterEngine();
    private final BossEncounterJsonCodec codec = new BossEncounterJsonCodec();

    @Test
    void roundTripsResettingRuntimeCanonically() {
        CharacterId first = new CharacterId(UUID.randomUUID());
        CharacterId second = new CharacterId(UUID.randomUUID());
        BossEncounterRuntime runtime =
                successful(
                        engine.start(
                                new EncounterId(UUID.randomUUID()),
                                DefinitionId.of("encounter.boss.training_golem"),
                                UUID.randomUUID(),
                                List.of(first, second),
                                100));
        runtime = successful(engine.defeat(runtime, first, UUID.randomUUID(), 101)).runtime();
        runtime = successful(engine.defeat(runtime, second, UUID.randomUUID(), 102)).runtime();
        runtime = successful(engine.beginReset(runtime, UUID.randomUUID())).runtime();

        String encoded = codec.encode(runtime);
        BossEncounterRuntime decoded = codec.decode(encoded);

        assertEquals(runtime, decoded);
        assertEquals(encoded, codec.encode(decoded));
        assertTrue(
                encoded.indexOf(first.value().toString())
                        != encoded.indexOf(second.value().toString()));
    }

    @Test
    void roundTripsContributionEvidenceAndReadsLegacyV1() {
        CharacterId characterId = new CharacterId(UUID.randomUUID());
        BossEncounterRuntime runtime =
                successful(
                        engine.start(
                                new EncounterId(UUID.randomUUID()),
                                DefinitionId.of("encounter.boss.training_golem"),
                                UUID.randomUUID(),
                                List.of(characterId),
                                100));
        runtime =
                successful(
                                engine.recordRewardContribution(
                                        runtime,
                                        characterId,
                                        new RewardContribution(100, 2, 3, 1),
                                        UUID.randomUUID(),
                                        120))
                        .runtime();
        runtime = successful(engine.confirmVictory(runtime, UUID.randomUUID(), 140)).runtime();
        assertEquals(runtime, codec.decode(codec.encode(runtime)));

        String legacy =
                codec.encode(runtime)
                        .replace("\"schemaVersion\":2", "\"schemaVersion\":1")
                        .replaceFirst(
                                ",\"rewardEvidence\":\\[.*?],\"processedOperations\"",
                                ",\"processedOperations\"")
                        .replace(",\"victoryTick\":140", "");
        BossEncounterRuntime decodedLegacy = codec.decode(legacy);
        assertEquals(
                0,
                decodedLegacy.rewardEvidence().get(characterId).contribution().damageAndPosture());
        assertEquals(100, decodedLegacy.victoryTick().orElseThrow());
    }

    @Test
    void rejectsUnknownSchemaAndInvariantViolations() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("{\"schemaVersion\":3}"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        codec.decode(
                                "{\"schemaVersion\":1,\"encounterId\":\""
                                        + UUID.randomUUID()
                                        + "\",\"definitionId\":\"encounter.boss.training_golem\","
                                        + "\"checkpointInstanceId\":\""
                                        + UUID.randomUUID()
                                        + "\",\"phase\":\"ACTIVE\",\"attempt\":1,\"startedTick\":0,"
                                        + "\"participants\":[],\"processedOperations\":[],"
                                        + "\"activeResetOperationId\":null,\"rewardGrantId\":null}"));
    }

    private static <T> T successful(Result<T, BossEncounterErrorCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<T, BossEncounterErrorCode>) result).value();
    }
}
