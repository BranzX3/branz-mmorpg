package com.branz.mmorpg.items.definition;

import java.util.Objects;

/** Off-hand shield identity and its authored guard authority. */
public record ShieldProfile(GuardCombatProfile guardProfile) {
    public ShieldProfile {
        Objects.requireNonNull(guardProfile, "guardProfile");
    }
}
