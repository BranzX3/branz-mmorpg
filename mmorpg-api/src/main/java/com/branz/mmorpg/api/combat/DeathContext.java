package com.branz.mmorpg.api.combat;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Complete server-authored context for one resolved death. */
public record DeathContext(
        UUID victimId,
        UUID killerId,
        DamageType cause,
        double overkill,
        UUID castId,
        Instant occurredAt,
        List<CombatContribution> contributions) {

    public DeathContext {
        Objects.requireNonNull(victimId, "victimId");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(castId, "castId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(contributions, "contributions");
        if (!Double.isFinite(overkill) || overkill < 0.0) {
            throw new IllegalArgumentException("overkill must be finite and non-negative");
        }
        contributions = List.copyOf(contributions);
    }
}
