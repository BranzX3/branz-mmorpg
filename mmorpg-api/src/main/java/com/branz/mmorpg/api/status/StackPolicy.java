package com.branz.mmorpg.api.status;

/**
 * What happens when a status is applied to a target that already has it.
 *
 * <p>Chosen per definition rather than globally, because the right answer
 * differs by effect: a stun that refreshed forever would be a permanent stun,
 * while a damage-over-time that could not stack would make a second caster
 * pointless.
 */
public enum StackPolicy {

    /** First application wins. Later ones are rejected until it expires. */
    UNIQUE,

    /** Duration is reset. Stack count stays at one. */
    REFRESH_DURATION,

    /** Stack count increases up to the maximum, and the duration is reset. */
    ADD_STACK_REFRESH,

    /**
     * Each application is its own instance with its own duration and its own
     * source. Used where attribution matters — three bleeds from three players
     * each credit their own caster.
     */
    INDEPENDENT_STACKS,

    /**
     * A stronger application replaces a weaker one; a weaker one is rejected.
     * Strength is the definition's potency, then the remaining duration.
     */
    REPLACE_WEAKER
}
