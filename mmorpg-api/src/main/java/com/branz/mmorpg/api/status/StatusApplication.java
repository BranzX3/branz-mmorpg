package com.branz.mmorpg.api.status;

/**
 * Outcome of applying a status.
 *
 * <p>A rejection is a first-class result, not an exception and not silence: an
 * immune target must produce a visible "immune" response, and a caster who
 * spent resources deserves to know the application did nothing.
 *
 * @param outcome  what happened
 * @param instance resulting instance, null when rejected
 * @param reason   detail for rejections and logs
 */
public record StatusApplication(Outcome outcome, StatusInstance instance, String reason) {

    public enum Outcome {
        /** A new instance was created. */
        APPLIED,
        /** An existing instance had its duration reset. */
        REFRESHED,
        /** An existing instance gained a stack. */
        STACKED,
        /** A weaker instance was replaced. */
        REPLACED,
        /** Target is immune to this status or its crowd-control class. */
        REJECTED_IMMUNE,
        /** An equal or stronger instance is already present. */
        REJECTED_WEAKER,
        /** Already at maximum stacks under a policy that does not refresh. */
        REJECTED_AT_CAP
    }

    public static StatusApplication of(Outcome outcome, StatusInstance instance) {
        return new StatusApplication(outcome, instance, null);
    }

    public static StatusApplication rejected(Outcome outcome, String reason) {
        return new StatusApplication(outcome, null, reason);
    }

    public boolean applied() {
        return instance != null;
    }
}
