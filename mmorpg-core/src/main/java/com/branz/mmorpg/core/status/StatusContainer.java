package com.branz.mmorpg.core.status;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.stat.ModifierSource;
import com.branz.mmorpg.api.status.CrowdControlCategory;
import com.branz.mmorpg.api.status.StackPolicy;
import com.branz.mmorpg.api.status.StatusApplication;
import com.branz.mmorpg.api.status.StatusCategory;
import com.branz.mmorpg.api.status.StatusDefinition;
import com.branz.mmorpg.api.status.StatusInstance;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Active statuses on one target.
 *
 * <p>Holds no timers. Time only moves when {@link #advance(Instant)} is called by
 * the central wheel, which is what keeps ten thousand effects from becoming ten
 * thousand scheduled tasks.
 *
 * <p>Not thread-safe: a target's statuses are owned by the thread that owns the
 * target.
 */
public final class StatusContainer {

    private static final AtomicLong INSTANCE_IDS = new AtomicLong();

    private final Map<Long, StatusInstance> instances = new LinkedHashMap<>();
    private final Set<CrowdControlCategory> immunities = EnumSet.noneOf(CrowdControlCategory.class);
    private final Set<ContentId> statusImmunities = new java.util.HashSet<>();

    /**
     * Applies a status.
     *
     * @param definition        what to apply
     * @param source            who applied it
     * @param requestedDuration duration override, or null to use the definition's
     * @param ccResistance      the target's crowd-control resistance as a fraction;
     *                          applied once, here, so no later step can apply it again
     * @param now               current instant
     */
    public StatusApplication apply(StatusDefinition definition,
                                   ModifierSource source,
                                   Duration requestedDuration,
                                   double ccResistance,
                                   Instant now) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(now, "now");

        if (statusImmunities.contains(definition.id())) {
            return StatusApplication.rejected(StatusApplication.Outcome.REJECTED_IMMUNE,
                    "immune to " + definition.id());
        }
        if (definition.crowdControlling() && immunities.contains(definition.crowdControl())) {
            return StatusApplication.rejected(StatusApplication.Outcome.REJECTED_IMMUNE,
                    "immune to " + definition.crowdControl());
        }

        Instant expiry = expiryFor(definition, requestedDuration, ccResistance, now);
        StatusInstance existing = findByDefinition(definition.id());

        if (existing == null || definition.stackPolicy() == StackPolicy.INDEPENDENT_STACKS) {
            return StatusApplication.of(StatusApplication.Outcome.APPLIED,
                    store(newInstance(definition, source, expiry, now)));
        }

        return switch (definition.stackPolicy()) {
            case UNIQUE -> StatusApplication.rejected(StatusApplication.Outcome.REJECTED_WEAKER,
                    definition.id() + " is already active and is UNIQUE");
            case REFRESH_DURATION -> StatusApplication.of(StatusApplication.Outcome.REFRESHED,
                    store(existing.withExpiry(laterOf(existing.expiresAt(), expiry))));
            case ADD_STACK_REFRESH -> {
                if (existing.stacks() >= definition.maxStacks()) {
                    // At the cap the duration still refreshes: the effect is
                    // maintained, it simply cannot grow stronger.
                    yield StatusApplication.of(StatusApplication.Outcome.REFRESHED,
                            store(existing.withExpiry(laterOf(existing.expiresAt(), expiry))));
                }
                yield StatusApplication.of(StatusApplication.Outcome.STACKED,
                        store(existing.withStacks(existing.stacks() + 1)
                                .withExpiry(laterOf(existing.expiresAt(), expiry))));
            }
            case REPLACE_WEAKER -> {
                if (!strongerThan(expiry, existing, now)) {
                    yield StatusApplication.rejected(StatusApplication.Outcome.REJECTED_WEAKER,
                            "an equal or stronger " + definition.id() + " is active");
                }
                instances.remove(existing.instanceId());
                yield StatusApplication.of(StatusApplication.Outcome.REPLACED,
                        store(newInstance(definition, source, expiry, now)));
            }
            case INDEPENDENT_STACKS -> throw new IllegalStateException("handled above");
        };
    }

    /**
     * Advances time, expiring statuses and collecting periodic ticks that came
     * due.
     *
     * <p>Catch-up is bounded: a tick that is overdue by many intervals fires once
     * and re-bases from {@code now}. Without that, a paused server would deliver
     * a burst of damage-over-time on resume and kill everyone who was burning.
     *
     * @return what happened, for the caller to turn into damage, healing, and events
     */
    public StatusTickResult advance(Instant now) {
        Objects.requireNonNull(now, "now");
        List<StatusInstance> expired = new ArrayList<>();
        List<StatusInstance> ticked = new ArrayList<>();

        for (StatusInstance instance : List.copyOf(instances.values())) {
            if (instance.expiredAt(now)) {
                instances.remove(instance.instanceId());
                expired.add(instance);
                continue;
            }
            if (instance.tickDueAt(now)) {
                ticked.add(instance);
            }
        }
        return new StatusTickResult(List.copyOf(ticked), List.copyOf(expired));
    }

    /**
     * Records that a periodic tick was delivered and schedules the next one.
     * Called by the wheel after the tick's effect has been applied.
     */
    public void tickDelivered(StatusInstance instance, Duration interval, Instant now) {
        StatusInstance current = instances.get(instance.instanceId());
        if (current == null) {
            return;
        }
        instances.put(current.instanceId(), current.withNextTick(now.plus(interval)));
    }

    /** Removes one instance. */
    public boolean remove(long instanceId) {
        return instances.remove(instanceId) != null;
    }

    /** Removes every instance of a definition. */
    public int removeDefinition(ContentId definitionId) {
        int before = instances.size();
        instances.values().removeIf(instance -> instance.definitionId().equals(definitionId));
        return before - instances.size();
    }

    /**
     * Cleanses statuses matching a dispel tag.
     *
     * @param category restrict to this category, or null for any
     * @param tag      dispel tag to match, or null for any
     * @param lookup   resolves a definition ID to its definition
     * @return the removed instances
     */
    public List<StatusInstance> cleanse(StatusCategory category, String tag,
                                        java.util.function.Function<ContentId, StatusDefinition> lookup) {
        Objects.requireNonNull(lookup, "lookup");
        List<StatusInstance> removed = new ArrayList<>();
        for (StatusInstance instance : List.copyOf(instances.values())) {
            StatusDefinition definition = lookup.apply(instance.definitionId());
            if (definition == null) {
                continue;
            }
            if (category != null && definition.category() != category) {
                continue;
            }
            if (tag != null && !definition.hasDispelTag(tag)) {
                continue;
            }
            instances.remove(instance.instanceId());
            removed.add(instance);
        }
        return List.copyOf(removed);
    }

    /** Drops everything. Used on death and on logout for CLEAR statuses. */
    public List<StatusInstance> clear() {
        List<StatusInstance> removed = List.copyOf(instances.values());
        instances.clear();
        return removed;
    }

    public void grantImmunity(CrowdControlCategory category) {
        immunities.add(Objects.requireNonNull(category, "category"));
    }

    public void grantImmunity(ContentId definitionId) {
        statusImmunities.add(Objects.requireNonNull(definitionId, "definitionId"));
    }

    public void revokeImmunity(CrowdControlCategory category) {
        immunities.remove(category);
    }

    public boolean has(ContentId definitionId) {
        return findByDefinition(definitionId) != null;
    }

    public int stacksOf(ContentId definitionId) {
        StatusInstance instance = findByDefinition(definitionId);
        return instance == null ? 0 : instance.stacks();
    }

    public int size() {
        return instances.size();
    }

    public List<StatusInstance> active() {
        return List.copyOf(instances.values());
    }

    private StatusInstance store(StatusInstance instance) {
        instances.put(instance.instanceId(), instance);
        return instance;
    }

    private StatusInstance newInstance(StatusDefinition definition, ModifierSource source,
                                       Instant expiry, Instant now) {
        Instant nextTick = definition.periodic() ? now.plus(definition.periodicInterval()) : null;
        return new StatusInstance(INSTANCE_IDS.incrementAndGet(), definition.id(), source,
                1, now, expiry, nextTick);
    }

    private StatusInstance findByDefinition(ContentId definitionId) {
        for (StatusInstance instance : instances.values()) {
            if (instance.definitionId().equals(definitionId)) {
                return instance;
            }
        }
        return null;
    }

    private static Instant expiryFor(StatusDefinition definition, Duration requestedDuration,
                                     double ccResistance, Instant now) {
        Duration duration = requestedDuration != null ? requestedDuration : definition.defaultDuration();
        if (duration.isZero() || duration.isNegative()) {
            return definition.permanent() && requestedDuration == null ? null : now;
        }
        if (definition.crowdControl().resistible() && ccResistance > 0.0) {
            // Applied exactly once, at application time. Applying it again on
            // each tick would compound into near-immunity.
            double factor = Math.max(0.0, 1.0 - Math.min(1.0, ccResistance));
            duration = Duration.ofMillis(Math.round(duration.toMillis() * factor));
        }
        return now.plus(duration);
    }

    private static Instant laterOf(Instant left, Instant right) {
        if (left == null || right == null) {
            return null;
        }
        return left.isAfter(right) ? left : right;
    }

    private static boolean strongerThan(Instant candidateExpiry, StatusInstance existing, Instant now) {
        if (candidateExpiry == null) {
            return true;
        }
        if (existing.permanent()) {
            return false;
        }
        return candidateExpiry.toEpochMilli() - now.toEpochMilli() > existing.remainingMillis(now);
    }
}
