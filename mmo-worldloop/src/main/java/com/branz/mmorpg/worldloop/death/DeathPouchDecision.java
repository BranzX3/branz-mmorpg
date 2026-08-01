package com.branz.mmorpg.worldloop.death;

import java.util.Objects;
import java.util.Optional;

public record DeathPouchDecision(DeathPouchDecisionReason reason, Optional<DeathPouchDraft> draft) {
    public DeathPouchDecision {
        Objects.requireNonNull(reason, "reason");
        draft = Objects.requireNonNull(draft, "draft");
        if ((reason == DeathPouchDecisionReason.POUCH_PLANNED) != draft.isPresent()) {
            throw new IllegalArgumentException("only a planned pouch may contain a draft");
        }
    }
}
