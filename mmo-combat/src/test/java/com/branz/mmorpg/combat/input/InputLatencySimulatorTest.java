package com.branz.mmorpg.combat.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class InputLatencySimulatorTest {
    private final InputLatencySimulator simulator = new InputLatencySimulator();

    @Test
    void zeroDelayArrivesAtNextServerTickAndJitterMayReorderLaterEmissions() {
        List<LatencyInputFrame> frames =
                simulator.deliver(
                        List.of(
                                emission(1, 0, 0, 0, SemanticInput.PRIMARY, "PRIMARY"),
                                emission(2, 1, 4, 0, SemanticInput.SECONDARY, "SECONDARY"),
                                emission(3, 2, 2, -2, SemanticInput.DODGE, "DODGE")));

        assertEquals(
                List.of(1L, 3L, 6L), frames.stream().map(LatencyInputFrame::deliveryTick).toList());
        assertEquals(SemanticInput.PRIMARY, frames.get(0).observations().getFirst().input());
        assertEquals(SemanticInput.DODGE, frames.get(1).observations().getFirst().input());
        assertEquals(SemanticInput.SECONDARY, frames.get(2).observations().getFirst().input());
    }

    @Test
    void shuffledEmissionCollectionsProduceIdenticalFrames() {
        List<LatencyInputEmission> ordered =
                List.of(
                        emission(1, 0, 2, 1, SemanticInput.PRIMARY, "PRIMARY"),
                        emission(2, 1, 1, 1, SemanticInput.DODGE, "DODGE"),
                        emission(3, 2, 0, 1, SemanticInput.SECONDARY, "SECONDARY"));
        List<LatencyInputFrame> expected = simulator.deliver(ordered);

        for (int seed = 0; seed < 1_000; seed++) {
            ArrayList<LatencyInputEmission> shuffled = new ArrayList<>(ordered);
            Collections.shuffle(shuffled, new java.util.Random(seed));
            assertEquals(expected, simulator.deliver(shuffled));
        }
        assertEquals(
                List.of(SemanticInput.PRIMARY, SemanticInput.DODGE, SemanticInput.SECONDARY),
                expected.getFirst().observations().stream().map(InputObservation::input).toList());
    }

    @Test
    void deliveredPacketAndBukkitDuplicatesStillCollapseInRouterWindow() {
        InputDeduplicationKey key = new InputDeduplicationKey("MAIN_HAND", "USE");
        List<LatencyInputFrame> frames =
                simulator.deliver(
                        List.of(
                                new LatencyInputEmission(
                                        1,
                                        0,
                                        1,
                                        0,
                                        SemanticInput.SECONDARY,
                                        DirectionSnapshot.NEUTRAL,
                                        "SECONDARY",
                                        key),
                                new LatencyInputEmission(
                                        2,
                                        0,
                                        2,
                                        0,
                                        SemanticInput.SECONDARY,
                                        DirectionSnapshot.NEUTRAL,
                                        "SECONDARY",
                                        key)));
        InputRouter router = new InputRouter();

        assertTrue(router.observe(frames.get(0).observations().getFirst()).isSuccess());
        Result<CombatInputRequest, InputRejectionCode> duplicate =
                router.observe(frames.get(1).observations().getFirst());
        assertEquals(
                InputRejectionCode.DUPLICATE_OBSERVATION,
                ((Result.Failure<CombatInputRequest, InputRejectionCode>) duplicate).error());
    }

    @Test
    void invalidDelayAndDuplicateClientSequenceFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> emission(1, 0, 40, 1, SemanticInput.PRIMARY, "PRIMARY"));
        LatencyInputEmission duplicate = emission(1, 0, 0, 0, SemanticInput.PRIMARY, "PRIMARY");
        assertThrows(
                IllegalArgumentException.class,
                () -> simulator.deliver(List.of(duplicate, duplicate)));
    }

    private static LatencyInputEmission emission(
            long sequence,
            long emittedTick,
            int latency,
            int jitter,
            SemanticInput input,
            String branch) {
        return new LatencyInputEmission(
                sequence,
                emittedTick,
                latency,
                jitter,
                input,
                DirectionSnapshot.NEUTRAL,
                branch,
                new InputDeduplicationKey("MAIN_HAND", sequence + ":" + input));
    }
}
