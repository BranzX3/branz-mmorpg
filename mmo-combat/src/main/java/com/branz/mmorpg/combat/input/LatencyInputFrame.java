package com.branz.mmorpg.combat.input;

import java.util.List;
import java.util.Objects;

/** Inputs delivered at one server scheduling boundary, ordered by synthetic client sequence. */
public record LatencyInputFrame(long deliveryTick, List<InputObservation> observations) {
    public LatencyInputFrame {
        if (deliveryTick < 1) {
            throw new IllegalArgumentException("deliveryTick must be positive");
        }
        observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
        if (observations.isEmpty()
                || observations.stream()
                        .anyMatch(observation -> observation.tick() != deliveryTick)) {
            throw new IllegalArgumentException("frame observations must share its delivery tick");
        }
    }
}
