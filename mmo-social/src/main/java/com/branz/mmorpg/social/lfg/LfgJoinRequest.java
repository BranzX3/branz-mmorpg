package com.branz.mmorpg.social.lfg;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;

public record LfgJoinRequest(
        CharacterId applicantId, LfgApplicantProfile profile, long requestedTick) {
    public LfgJoinRequest {
        Objects.requireNonNull(applicantId, "applicantId");
        Objects.requireNonNull(profile, "profile");
        if (requestedTick < 0) {
            throw new IllegalArgumentException("requestedTick must not be negative");
        }
    }
}
