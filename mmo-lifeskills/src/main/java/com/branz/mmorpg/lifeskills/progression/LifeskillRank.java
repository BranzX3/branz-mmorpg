package com.branz.mmorpg.lifeskills.progression;

import java.util.Objects;
import java.util.Optional;

/** One of the thirty visible V1 ranks from Trainee I through Grandmaster V. */
public record LifeskillRank(LifeskillRankTier tier, int grade) {
    public static final int GRADES_PER_TIER = 5;
    public static final int RANK_COUNT = LifeskillRankTier.values().length * GRADES_PER_TIER;

    public LifeskillRank {
        Objects.requireNonNull(tier, "tier");
        if (grade < 1 || grade > GRADES_PER_TIER) {
            throw new IllegalArgumentException("lifeskill rank grade must be in [1, 5]");
        }
    }

    public static LifeskillRank initial() {
        return new LifeskillRank(LifeskillRankTier.TRAINEE, 1);
    }

    public static LifeskillRank fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= RANK_COUNT) {
            throw new IllegalArgumentException("lifeskill rank ordinal must be in [0, 29]");
        }
        LifeskillRankTier tier = LifeskillRankTier.values()[ordinal / GRADES_PER_TIER];
        return new LifeskillRank(tier, ordinal % GRADES_PER_TIER + 1);
    }

    public int ordinal() {
        return tier.ordinal() * GRADES_PER_TIER + grade - 1;
    }

    public Optional<LifeskillRank> next() {
        return ordinal() + 1 < RANK_COUNT
                ? Optional.of(fromOrdinal(ordinal() + 1))
                : Optional.empty();
    }

    public String displayName() {
        return tier.displayName() + " " + roman(grade);
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> throw new IllegalArgumentException("unsupported grade");
        };
    }
}
