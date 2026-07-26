package com.branz.mmorpg.api.status;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentType;
import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.stat.AttributeModifier;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable definition of a status effect.
 *
 * <p>Declarative by design: a definition describes what a status <em>is</em>, and
 * the engine decides what that means. Content may not name a Java class or run a
 * command, so a bad definition can only ever be rejected by validation, never
 * execute anything.
 *
 * @param id               stable content ID
 * @param displayName      player-facing name
 * @param category         positive, negative, or neutral — drives cleanse rules and UI
 * @param stackPolicy      behaviour on re-application
 * @param maxStacks        cap for stacking policies, at least 1
 * @param defaultDuration  duration when the applier does not override it; zero means permanent
 * @param periodicInterval interval between periodic ticks; zero means no periodic effect
 * @param potency          per-stack magnitude of the periodic effect (damage, healing, absorb)
 * @param modifiers        attribute modifiers granted while active
 * @param dispelTags       tags a cleanse can match
 * @param crowdControl     crowd-control category, {@link CrowdControlCategory#NONE} when not CC
 * @param offlinePolicy    what happens to the remaining duration while the player is offline
 */
public record StatusDefinition(
        ContentId id,
        String displayName,
        StatusCategory category,
        StackPolicy stackPolicy,
        int maxStacks,
        Duration defaultDuration,
        Duration periodicInterval,
        double potency,
        List<AttributeModifier> modifiers,
        Set<String> dispelTags,
        CrowdControlCategory crowdControl,
        OfflinePolicy offlinePolicy) implements ContentDefinition {

    public StatusDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(stackPolicy, "stackPolicy");
        Objects.requireNonNull(defaultDuration, "defaultDuration");
        Objects.requireNonNull(periodicInterval, "periodicInterval");
        Objects.requireNonNull(modifiers, "modifiers");
        Objects.requireNonNull(dispelTags, "dispelTags");
        Objects.requireNonNull(crowdControl, "crowdControl");
        Objects.requireNonNull(offlinePolicy, "offlinePolicy");
        displayName = displayName == null || displayName.isBlank() ? id.value() : displayName.trim();
        if (maxStacks < 1) {
            throw new MMOException(ErrorCode.CONTENT_INVALID,
                    id + ": maxStacks must be at least 1, was " + maxStacks);
        }
        if (defaultDuration.isNegative() || periodicInterval.isNegative()) {
            throw new MMOException(ErrorCode.CONTENT_INVALID, id + ": durations must not be negative");
        }
        if (!Double.isFinite(potency)) {
            throw new MMOException(ErrorCode.CONTENT_INVALID, id + ": potency must be finite");
        }
        if (crowdControl != CrowdControlCategory.NONE && defaultDuration.isZero()) {
            // A permanent stun is never a balance decision, it is a bug.
            throw new MMOException(ErrorCode.CONTENT_INVALID,
                    id + ": crowd control must have a finite duration");
        }
        if (maxStacks > 1 && stackPolicy == StackPolicy.UNIQUE) {
            throw new MMOException(ErrorCode.CONTENT_INVALID,
                    id + ": UNIQUE cannot declare more than one stack");
        }
        modifiers = List.copyOf(modifiers);
        dispelTags = Set.copyOf(dispelTags);
    }

    public boolean periodic() {
        return !periodicInterval.isZero();
    }

    @Override
    public ContentType type() {
        return ContentType.STATUS;
    }

    public boolean permanent() {
        return defaultDuration.isZero();
    }

    public boolean crowdControlling() {
        return crowdControl != CrowdControlCategory.NONE;
    }

    public boolean hasDispelTag(String tag) {
        return dispelTags.contains(tag);
    }
}
