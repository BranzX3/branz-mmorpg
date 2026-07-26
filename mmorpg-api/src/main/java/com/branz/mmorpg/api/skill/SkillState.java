package com.branz.mmorpg.api.skill;

/** Runtime phase of one cast. */
public enum SkillState {
    CASTING,
    ACTIVE,
    RECOVERY,
    COOLDOWN,
    COMPLETE,
    INTERRUPTED
}
