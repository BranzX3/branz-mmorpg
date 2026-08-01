package com.branz.mmorpg.lifeskills.progression;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** Content-authored cumulative evidence thresholds for all thirty V1 ranks. */
public final class LifeskillRankTable {
    private final List<Double> minimumEvidenceByRank;

    public LifeskillRankTable(List<Double> minimumEvidenceByRank) {
        this.minimumEvidenceByRank =
                List.copyOf(Objects.requireNonNull(minimumEvidenceByRank, "minimumEvidenceByRank"));
        if (this.minimumEvidenceByRank.size() != LifeskillRank.RANK_COUNT) {
            throw new IllegalArgumentException("rank table must define exactly thirty thresholds");
        }
        double previous = -1;
        for (int index = 0; index < this.minimumEvidenceByRank.size(); index++) {
            double threshold = this.minimumEvidenceByRank.get(index);
            if (!Double.isFinite(threshold)
                    || threshold < 0
                    || (index == 0 && threshold != 0)
                    || threshold <= previous) {
                throw new IllegalArgumentException(
                        "rank thresholds must start at zero and increase strictly");
            }
            previous = threshold;
        }
    }

    public LifeskillRank rankAt(double evidence) {
        requireEvidence(evidence);
        int ordinal = 0;
        for (int index = 1; index < minimumEvidenceByRank.size(); index++) {
            if (evidence < minimumEvidenceByRank.get(index)) {
                break;
            }
            ordinal = index;
        }
        return LifeskillRank.fromOrdinal(ordinal);
    }

    public OptionalDouble nextThreshold(LifeskillRank rank) {
        Objects.requireNonNull(rank, "rank");
        int next = rank.ordinal() + 1;
        return next < minimumEvidenceByRank.size()
                ? OptionalDouble.of(minimumEvidenceByRank.get(next))
                : OptionalDouble.empty();
    }

    public double maximumEffectiveEvidence() {
        return minimumEvidenceByRank.getLast();
    }

    private static void requireEvidence(double evidence) {
        if (!Double.isFinite(evidence) || evidence < 0) {
            throw new IllegalArgumentException(
                    "lifeskill evidence must be finite and non-negative");
        }
    }
}
