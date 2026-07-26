package com.branz.mmorpg.core.combat;

import com.branz.mmorpg.api.combat.CombatContribution;
import com.branz.mmorpg.api.combat.DamageResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Authoritative, in-memory damage/threat ledger for active combat targets. */
public final class ContributionTracker {
    private static final Comparator<CombatContribution> ORDER = Comparator
            .comparingDouble(CombatContribution::effectiveDamage).reversed()
            .thenComparing(value -> value.contributorId().toString());

    private final Map<UUID, Map<UUID, MutableContribution>> byTarget = new HashMap<>();

    public synchronized void record(UUID targetId, UUID contributorId,
                                    DamageResult result, Instant occurredAt) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(contributorId, "contributorId");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (!result.landed() || (result.applied() <= 0.0 && result.absorbed() <= 0.0)) return;
        MutableContribution contribution = byTarget
                .computeIfAbsent(targetId, ignored -> new HashMap<>())
                .computeIfAbsent(contributorId, ignored -> new MutableContribution());
        contribution.healthDamage += result.applied();
        contribution.shieldDamage += result.absorbed();
        contribution.threat += result.applied() + result.absorbed();
        contribution.hitCount++;
        contribution.lastContributionAt = occurredAt;
    }

    public synchronized List<CombatContribution> snapshot(UUID targetId) {
        Map<UUID, MutableContribution> values = byTarget.get(targetId);
        if (values == null) return List.of();
        List<CombatContribution> result = new ArrayList<>(values.size());
        values.forEach((contributorId, value) -> result.add(value.snapshot(contributorId)));
        result.sort(ORDER);
        return List.copyOf(result);
    }

    public synchronized List<CombatContribution> complete(UUID targetId) {
        List<CombatContribution> result = snapshot(targetId);
        byTarget.remove(targetId);
        return result;
    }

    public synchronized void forgetTarget(UUID targetId) {
        byTarget.remove(targetId);
    }

    public synchronized int trackedTargets() {
        return byTarget.size();
    }

    private static final class MutableContribution {
        private double healthDamage;
        private double shieldDamage;
        private double threat;
        private int hitCount;
        private Instant lastContributionAt;

        private CombatContribution snapshot(UUID contributorId) {
            return new CombatContribution(contributorId, healthDamage, shieldDamage,
                    threat, hitCount, lastContributionAt);
        }
    }
}
