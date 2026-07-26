package com.branz.mmorpg.core.stat;

import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.api.stat.AttributeModifier;
import com.branz.mmorpg.api.stat.AttributeSnapshot;
import com.branz.mmorpg.api.stat.AttributeType;
import com.branz.mmorpg.api.stat.ModifierSource;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Live modifier set for one entity, with a cached resolved snapshot.
 *
 * <p>Modifiers are keyed by ID, so re-adding the same one replaces it. That is
 * the mechanism behind the "equipment swap cannot duplicate modifiers"
 * requirement: an item's modifier IDs are derived from its instance, so
 * applying them twice is indistinguishable from applying them once.
 *
 * <p>The snapshot is recomputed lazily and only when something actually changed,
 * because combat reads attributes far more often than buffs change.
 *
 * <p>Not thread-safe: an entity's attributes are owned by the thread that owns
 * the entity.
 */
public final class AttributeContainer {

    private final Map<String, AttributeModifier> modifiers = new LinkedHashMap<>();
    private final Map<AttributeType, Double> bases = new EnumMap<>(AttributeType.class);
    private AttributeSnapshot cached;
    private Instant cachedAt = Instant.MIN;

    /** Sets the unmodified base of an attribute. */
    public void base(AttributeType attribute, double value) {
        Objects.requireNonNull(attribute, "attribute");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("base for " + attribute + " must be finite: " + value);
        }
        if (attribute.resourceMaximum() && value < 0) {
            throw new IllegalArgumentException("resource maximum must not be negative: " + attribute);
        }
        bases.put(attribute, value);
        invalidate();
    }

    public double base(AttributeType attribute) {
        return bases.getOrDefault(attribute, attribute.defaultValue());
    }

    /**
     * Adds or replaces a modifier.
     *
     * @return true when this changed the container
     */
    public boolean add(AttributeModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        AttributeModifier previous = modifiers.put(modifier.id(), modifier);
        if (modifier.equals(previous)) {
            return false;
        }
        invalidate();
        return true;
    }

    public boolean remove(String modifierId) {
        Objects.requireNonNull(modifierId, "modifierId");
        if (modifiers.remove(modifierId) == null) {
            return false;
        }
        invalidate();
        return true;
    }

    /** Removes every modifier granted by {@code source}. Used on unequip and cleanse. */
    public int removeSource(ModifierSource source) {
        Objects.requireNonNull(source, "source");
        int before = modifiers.size();
        modifiers.values().removeIf(modifier -> modifier.source().equals(source));
        if (modifiers.size() != before) {
            invalidate();
        }
        return before - modifiers.size();
    }

    /**
     * Drops modifiers that have lapsed.
     *
     * <p>Expiry is evaluated here rather than by a timer per modifier: one sweep
     * on the tick that needs it beats thousands of scheduled tasks, and it means
     * a lapsed modifier can never be observed as still active.
     *
     * @return how many were removed
     */
    public int purgeExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        int before = modifiers.size();
        modifiers.values().removeIf(modifier -> modifier.expiredAt(now));
        if (modifiers.size() != before) {
            invalidate();
        }
        return before - modifiers.size();
    }

    public boolean contains(String modifierId) {
        return modifiers.containsKey(modifierId);
    }

    public Optional<AttributeModifier> modifier(String modifierId) {
        return Optional.ofNullable(modifiers.get(Objects.requireNonNull(modifierId, "modifierId")));
    }

    public int size() {
        return modifiers.size();
    }

    public List<AttributeModifier> modifiers() {
        return List.copyOf(modifiers.values());
    }

    /** Modifiers that currently contribute to {@code attribute}, in applied order. */
    public List<AttributeModifier> contributing(AttributeType attribute) {
        return AttributeResolver.applicable(attribute, modifiers.values());
    }

    public double value(AttributeType attribute) {
        return AttributeResolver.resolve(attribute, base(attribute), modifiers.values());
    }

    /**
     * Resolved snapshot, purging anything expired at {@code now} first so a
     * lapsed buff can never appear in a resolved value.
     */
    public AttributeSnapshot snapshot(GameClock clock) {
        Objects.requireNonNull(clock, "clock");
        Instant now = clock.now();
        purgeExpired(now);
        if (cached != null && now.equals(cachedAt)) {
            return cached;
        }
        EnumMap<AttributeType, Double> values = new EnumMap<>(AttributeType.class);
        for (AttributeType attribute : AttributeType.values()) {
            values.put(attribute, value(attribute));
        }
        cached = new AttributeSnapshot(values);
        cachedAt = now;
        return cached;
    }

    private void invalidate() {
        cached = null;
        cachedAt = Instant.MIN;
    }
}
