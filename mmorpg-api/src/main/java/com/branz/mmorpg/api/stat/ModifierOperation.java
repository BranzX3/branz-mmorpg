package com.branz.mmorpg.api.stat;

/**
 * How a modifier contributes to a resolved attribute.
 *
 * <p>The order is fixed and is the whole reason this enum is ordered rather than
 * free-form: {@code +10 flat then +50%} and {@code +50% then +10 flat} give
 * different numbers, so the pipeline applies every {@link #ADD_FLAT}, then sums
 * all {@link #ADD_PERCENT} into one additive group, then applies each
 * {@link #MULTIPLY} in turn.
 *
 * <p>Additive percentages are summed rather than compounded so that stacking
 * five 10% sources is a predictable +50%, not +61%.
 */
public enum ModifierOperation {

    /** Added to the base before any percentage applies. */
    ADD_FLAT,

    /** Summed with every other ADD_PERCENT, then applied once. 0.10 = +10%. */
    ADD_PERCENT,

    /** Applied last, one factor at a time. 1.10 = x1.10. */
    MULTIPLY
}
