package com.branz.mmorpg.api.lifeskill;

/** Result of an idempotent Life Skill mutation committed by storage. */
public record LifeSkillMutationCommit(
        boolean applied,
        LifeSkillSnapshot before,
        LifeSkillSnapshot after) {
}
