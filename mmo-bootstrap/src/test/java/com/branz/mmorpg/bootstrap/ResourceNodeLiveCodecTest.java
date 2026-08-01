package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.result.ErrorCode;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.content.snapshot.ContentSnapshotLoader;
import com.branz.mmorpg.lifeskills.node.ResourceNodeEngine;
import com.branz.mmorpg.lifeskills.node.ResourceNodeId;
import com.branz.mmorpg.lifeskills.node.ResourceNodeReservationRequest;
import com.branz.mmorpg.lifeskills.node.ResourceNodeRuntime;
import com.branz.mmorpg.lifeskills.node.ResourceNodeTransition;
import com.branz.mmorpg.lifeskills.progression.LifeFocusRuntime;
import com.branz.mmorpg.lifeskills.progression.LifeskillRank;
import com.branz.mmorpg.lifeskills.progression.LifeskillRankRuntime;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResourceNodeLiveCodecTest {
    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void compilesPaperNodeLabFromExampleContent() {
        Path fixture = Path.of("..", "example-content", "milestone-1").toAbsolutePath().normalize();
        ContentSnapshot snapshot = success(new ContentSnapshotLoader().load(fixture));

        CompiledResourceNode compiled =
                ResourceNodeContentCompiler.compileFirst(snapshot).orElseThrow();

        assertEquals("node.frostpeak.iron_common", compiled.definition().id().value());
        assertEquals("lifeskill.mining", compiled.definition().discipline().id().value());
        assertEquals(36, compiled.definition().workDurationTicks());
        assertEquals(Set.of("tool.pickaxe"), compiled.definition().requiredToolTags());
        assertEquals("equipment.training_pickaxe", compiled.toolDefinitionId().value());
        assertEquals("material.iron_ore", compiled.outputDefinitionId().value());
        assertEquals(1, compiled.outputQuantity());
        assertEquals(10, compiled.rankEvidence());
        assertEquals(
                50,
                compiled.rankTable()
                        .nextThreshold(
                                com.branz.mmorpg.lifeskills.progression.LifeskillRank.fromOrdinal(
                                        4))
                        .orElseThrow());
    }

    @Test
    void roundTripsReservedNodeAndProgressionCanonically() {
        CompiledResourceNode compiled = compiled();
        CharacterId actor = new CharacterId(UUID.randomUUID());
        ResourceNodeRuntime initial =
                ResourceNodeRuntime.initial(
                        new ResourceNodeId(UUID.randomUUID()), compiled.definition());
        ResourceNodeTransition reserved =
                success(
                        new ResourceNodeEngine()
                                .reserve(
                                        compiled.definition(),
                                        initial,
                                        new ResourceNodeReservationRequest(
                                                actor,
                                                UUID.randomUUID(),
                                                Set.of("tool.pickaxe"),
                                                100,
                                                true,
                                                true,
                                                3,
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                50,
                                                NOW)));
        ResourceNodeStateJsonCodec nodeCodec = new ResourceNodeStateJsonCodec();

        String encodedNode = nodeCodec.encode(reserved.runtime());

        assertEquals(reserved.runtime(), nodeCodec.decode(encodedNode));
        assertEquals(encodedNode, nodeCodec.encode(nodeCodec.decode(encodedNode)));

        ResourceNodeLifeskillState state =
                new ResourceNodeLifeskillState(
                        new LifeskillRankRuntime(
                                compiled.definition().discipline(),
                                20,
                                LifeskillRank.fromOrdinal(2),
                                Map.of(UUID.randomUUID(), 10.0)),
                        new LifeFocusRuntime(97, NOW, Map.of(UUID.randomUUID(), 3)));
        ResourceNodeLifeskillStateJsonCodec stateCodec = new ResourceNodeLifeskillStateJsonCodec();
        String encodedState = stateCodec.encode(state);

        assertEquals(state, stateCodec.decode(encodedState));
        assertEquals(encodedState, stateCodec.encode(stateCodec.decode(encodedState)));
    }

    @Test
    void toolPayloadPreservesFieldsAndFreezesOneReservation() {
        ResourceNodeToolPayloadCodec codec = new ResourceNodeToolPayloadCodec();
        UUID reservation = UUID.randomUUID();

        String reserved =
                codec.reserve(
                        "{\"displayRevision\":4,\"testProvenance\":\"dev:test\"}",
                        100,
                        reservation);
        String spent = codec.spend(reserved, 100, 2);

        assertEquals(Optional.of(reservation), codec.reservation(reserved));
        assertEquals(Optional.empty(), codec.reservation(spent));
        assertEquals(98, codec.durability(spent, 100));
        assertTrue(spent.contains("\"testProvenance\":\"dev:test\""));
        assertThrows(IllegalArgumentException.class, () -> codec.spend(spent, 100, 99));
    }

    private static CompiledResourceNode compiled() {
        Path fixture = Path.of("..", "example-content", "milestone-1").toAbsolutePath().normalize();
        return ResourceNodeContentCompiler.compileFirst(
                        success(new ContentSnapshotLoader().load(fixture)))
                .orElseThrow();
    }

    private static <T, E extends ErrorCode> T success(Result<T, E> result) {
        assertTrue(
                result.isSuccess(),
                () -> result instanceof Result.Failure<T, E> failure ? failure.detail() : "");
        return ((Result.Success<T, E>) result).value();
    }
}
