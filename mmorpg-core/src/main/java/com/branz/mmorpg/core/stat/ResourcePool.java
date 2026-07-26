package com.branz.mmorpg.core.stat;

import com.branz.mmorpg.api.stat.AttributeType;
import java.util.Objects;

/**
 * A current/maximum pair for Health, Mana, or Stamina.
 *
 * <p>Current value is runtime state and is never persisted as a ratio. When the
 * maximum drops — a buff lapses, an item comes off — the current value is
 * clamped down, deliberately not rescaled: preserving the ratio would hand a
 * player health back for losing an item, and repeated equip/unequip would be a
 * free heal.
 */
public final class ResourcePool {

    private final AttributeType maximumAttribute;
    private double maximum;
    private double current;

    public ResourcePool(AttributeType maximumAttribute, double maximum) {
        this.maximumAttribute = Objects.requireNonNull(maximumAttribute, "maximumAttribute");
        if (!maximumAttribute.resourceMaximum()) {
            throw new IllegalArgumentException(maximumAttribute + " is not a resource maximum");
        }
        this.maximum = requireValid(maximum, "maximum");
        this.current = this.maximum;
    }

    public AttributeType maximumAttribute() {
        return maximumAttribute;
    }

    public double maximum() {
        return maximum;
    }

    public double current() {
        return current;
    }

    public boolean depleted() {
        return current <= 0.0;
    }

    public double ratio() {
        return maximum <= 0.0 ? 0.0 : current / maximum;
    }

    /**
     * Applies a new maximum, clamping the current value into range.
     *
     * @return the current value after clamping
     */
    public double maximum(double newMaximum) {
        maximum = requireValid(newMaximum, "maximum");
        if (current > maximum) {
            current = maximum;
        }
        return current;
    }

    /** Adds {@code amount} (negative to spend), clamped to [0, maximum]. */
    public double add(double amount) {
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("amount must be finite: " + amount);
        }
        current = Math.min(maximum, Math.max(0.0, current + amount));
        return current;
    }

    /**
     * Spends {@code cost} only if the pool can pay in full.
     *
     * @return true when it was spent; a partial spend never happens
     */
    public boolean spend(double cost) {
        if (!Double.isFinite(cost) || cost < 0.0) {
            throw new IllegalArgumentException("cost must be finite and non-negative: " + cost);
        }
        if (current < cost) {
            return false;
        }
        current -= cost;
        return true;
    }

    public void fill() {
        current = maximum;
    }

    /**
     * Regenerates over {@code elapsedTicks} at {@code perSecond}.
     *
     * <p>Driven by elapsed ticks rather than by a fixed timer so a lagging or
     * catching-up server regenerates the correct amount instead of an amount
     * proportional to how often the task happened to run.
     *
     * @param inCombat when true, {@code combatFactor} scales the rate
     * @return the current value after regeneration
     */
    public double regenerate(double perSecond, long elapsedTicks, boolean inCombat, double combatFactor) {
        if (elapsedTicks <= 0 || perSecond <= 0.0) {
            return current;
        }
        if (!Double.isFinite(perSecond) || !Double.isFinite(combatFactor)) {
            throw new IllegalArgumentException("regeneration rates must be finite");
        }
        double rate = inCombat ? perSecond * Math.max(0.0, combatFactor) : perSecond;
        return add(rate * (elapsedTicks / 20.0));
    }

    private static double requireValid(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite: " + value);
        }
        if (value < 0.0) {
            throw new IllegalArgumentException(label + " must not be negative: " + value);
        }
        return value;
    }
}
