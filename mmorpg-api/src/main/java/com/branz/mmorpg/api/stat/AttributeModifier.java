package com.branz.mmorpg.api.stat;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One immutable contribution to one attribute.
 *
 * <p>{@code id} is the identity. Adding a modifier whose ID is already present
 * replaces it rather than stacking, which is what makes an equipment swap unable
 * to double a bonus: re-applying the same item's modifier is idempotent.
 *
 * <p>{@code stackingGroup} bounds sources that should not add up — three
 * different "+10% attack speed" buffs in group {@code haste} contribute only the
 * strongest. An empty group means the modifier always applies.
 *
 * <p>{@code priority} breaks ties deterministically: within a group the highest
 * priority wins, and equal priorities fall back to the modifier ID so resolution
 * never depends on insertion order or hash iteration order.
 *
 * @param id            stable identity; re-adding replaces
 * @param attribute     attribute affected
 * @param operation     how the value contributes
 * @param value         magnitude, finite
 * @param source        what granted it
 * @param stackingGroup group of mutually exclusive sources, empty when unbounded
 * @param priority      tie-break within a stacking group, higher wins
 * @param expiresAt     when it lapses, empty when permanent
 */
public record AttributeModifier(
        String id,
        AttributeType attribute,
        ModifierOperation operation,
        double value,
        ModifierSource source,
        String stackingGroup,
        int priority,
        Optional<Instant> expiresAt) implements Comparable<AttributeModifier> {

    public AttributeModifier {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (id.isBlank()) {
            throw new MMOException(ErrorCode.INVALID_ARGUMENT, "modifier id must not be blank");
        }
        if (!Double.isFinite(value)) {
            // NaN and infinity poison every later calculation and would be
            // persisted as a broken stat sheet, so they are refused at the door.
            throw new MMOException(ErrorCode.INVALID_ARGUMENT,
                    "modifier " + id + " value must be finite: " + value);
        }
        stackingGroup = stackingGroup == null ? "" : stackingGroup.trim();
    }

    public static AttributeModifier flat(String id, AttributeType attribute, double value,
                                         ModifierSource source) {
        return new AttributeModifier(id, attribute, ModifierOperation.ADD_FLAT, value, source,
                "", 0, Optional.empty());
    }

    public static AttributeModifier percent(String id, AttributeType attribute, double fraction,
                                            ModifierSource source) {
        return new AttributeModifier(id, attribute, ModifierOperation.ADD_PERCENT, fraction, source,
                "", 0, Optional.empty());
    }

    public AttributeModifier expiringAt(Instant expiry) {
        return new AttributeModifier(id, attribute, operation, value, source,
                stackingGroup, priority, Optional.ofNullable(expiry));
    }

    public AttributeModifier inGroup(String group, int groupPriority) {
        return new AttributeModifier(id, attribute, operation, value, source,
                group, groupPriority, expiresAt);
    }

    public boolean grouped() {
        return !stackingGroup.isEmpty();
    }

    public boolean expiredAt(Instant now) {
        return expiresAt.isPresent() && !now.isBefore(expiresAt.get());
    }

    /** Deterministic order: priority descending, then ID. Never insertion order. */
    @Override
    public int compareTo(AttributeModifier other) {
        int byPriority = Integer.compare(other.priority, priority);
        return byPriority != 0 ? byPriority : id.compareTo(other.id);
    }
}
