package com.branz.mmorpg.api.player;

import com.branz.mmorpg.api.lifeskill.LifeSkillProfile;
import java.util.Objects;
import java.util.UUID;

/** Durable recovery payload captured after a session save exhausts its retries. */
public record PendingSessionSave(PlayerProfile profile, LifeSkillProfile lifeSkills) {

    public PendingSessionSave {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(lifeSkills, "lifeSkills");
        if (!profile.playerId().equals(lifeSkills.playerId())) {
            throw new IllegalArgumentException("profile and Life Skill snapshot owners differ");
        }
    }

    public UUID playerId() {
        return profile.playerId();
    }
}
