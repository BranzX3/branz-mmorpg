package com.branz.mmorpg.api.mastery;

public record MasteryMutationCommit(
        boolean applied,
        MasterySnapshot before,
        MasterySnapshot after,
        long awardedXp) {
}
