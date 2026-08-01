package com.branz.mmorpg.lifeskills.progression;

import java.util.Objects;

public record LifeskillRankDecision(
        LifeskillRankRuntime runtime,
        LifeskillRank previousRank,
        double awardedEvidence,
        boolean replayed) {
    public LifeskillRankDecision {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(previousRank, "previousRank");
        if (!Double.isFinite(awardedEvidence) || awardedEvidence < 0) {
            throw new IllegalArgumentException("awardedEvidence must be finite and non-negative");
        }
        if (replayed != (awardedEvidence == 0)) {
            throw new IllegalArgumentException("only replayed decisions may award zero evidence");
        }
    }

    public boolean promoted() {
        return runtime.rank().ordinal() > previousRank.ordinal();
    }
}
