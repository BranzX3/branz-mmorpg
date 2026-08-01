package com.branz.mmorpg.social.lfg;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Objects;
import java.util.Optional;

public record LfgTransition(
        LfgListingRuntime runtime, Optional<CharacterId> acceptedApplicant, boolean changed) {
    public LfgTransition {
        Objects.requireNonNull(runtime, "runtime");
        acceptedApplicant = Objects.requireNonNull(acceptedApplicant, "acceptedApplicant");
        if (!changed && acceptedApplicant.isPresent()) {
            throw new IllegalArgumentException("unchanged transition cannot accept an applicant");
        }
    }

    static LfgTransition unchanged(LfgListingRuntime runtime) {
        return new LfgTransition(runtime, Optional.empty(), false);
    }
}
