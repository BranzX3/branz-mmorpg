package com.branz.mmorpg.combat.input;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Converts synthetic latency/jitter emissions into stable server-tick input frames. */
public final class InputLatencySimulator {
    public List<LatencyInputFrame> deliver(List<LatencyInputEmission> emissions) {
        Objects.requireNonNull(emissions, "emissions");
        Set<Long> sequences = new HashSet<>();
        Map<Long, List<LatencyInputEmission>> byTick = new TreeMap<>();
        for (LatencyInputEmission emission : emissions) {
            Objects.requireNonNull(emission, "emission");
            if (!sequences.add(emission.clientSequence())) {
                throw new IllegalArgumentException("client sequences must be unique");
            }
            byTick.computeIfAbsent(emission.deliveryTick(), ignored -> new ArrayList<>())
                    .add(emission);
        }
        ArrayList<LatencyInputFrame> frames = new ArrayList<>();
        byTick.forEach(
                (tick, delivered) -> {
                    delivered.sort(Comparator.comparingLong(LatencyInputEmission::clientSequence));
                    List<InputObservation> observations =
                            delivered.stream()
                                    .map(
                                            emission ->
                                                    new InputObservation(
                                                            tick,
                                                            emission.input(),
                                                            emission.direction(),
                                                            emission.branchFamily(),
                                                            emission.deduplicationKey()))
                                    .toList();
                    frames.add(new LatencyInputFrame(tick, observations));
                });
        return List.copyOf(frames);
    }
}
