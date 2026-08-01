package com.branz.mmorpg.lifeskills.progression;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record LifeskillRankRuntime(
        LifeskillDiscipline discipline,
        double evidence,
        LifeskillRank rank,
        Map<UUID, Double> processedOperations) {
    public LifeskillRankRuntime {
        Objects.requireNonNull(discipline, "discipline");
        if (!Double.isFinite(evidence) || evidence < 0) {
            throw new IllegalArgumentException(
                    "lifeskill evidence must be finite and non-negative");
        }
        Objects.requireNonNull(rank, "rank");
        processedOperations =
                Map.copyOf(Objects.requireNonNull(processedOperations, "processedOperations"));
        processedOperations.forEach(
                (operationId, amount) -> {
                    Objects.requireNonNull(operationId, "operationId");
                    if (amount == null || !Double.isFinite(amount) || amount <= 0) {
                        throw new IllegalArgumentException(
                                "processed lifeskill evidence must be finite and positive");
                    }
                });
    }

    public static LifeskillRankRuntime initial(LifeskillDiscipline discipline) {
        return new LifeskillRankRuntime(discipline, 0, LifeskillRank.initial(), Map.of());
    }
}
