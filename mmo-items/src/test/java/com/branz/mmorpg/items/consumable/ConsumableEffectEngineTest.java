package com.branz.mmorpg.items.consumable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.result.Result;
import org.junit.jupiter.api.Test;

class ConsumableEffectEngineTest {
    private final ConsumableEffectEngine engine = new ConsumableEffectEngine();

    @Test
    void replacesOnlyItsOwnCategoryAndRetainsOtherActiveCategories() {
        ConsumableEffectState first =
                success(
                        engine.apply(
                                ConsumableEffectState.empty(),
                                effect(
                                        "effect.ward.fire",
                                        ConsumableCategory.ELEMENTAL_WARD,
                                        false),
                                100,
                                false));
        ConsumableEffectState withMeal =
                success(
                        engine.apply(
                                first,
                                effect("effect.meal.stew", ConsumableCategory.MEAL, false),
                                100,
                                false));
        ConsumableEffectState replaced =
                success(
                        engine.apply(
                                withMeal,
                                effect(
                                        "effect.ward.frost",
                                        ConsumableCategory.ELEMENTAL_WARD,
                                        false),
                                100,
                                false));

        assertEquals(
                DefinitionId.of("effect.ward.frost"),
                replaced.effect(ConsumableCategory.ELEMENTAL_WARD, 100).orElseThrow().effectId());
        assertTrue(replaced.effect(ConsumableCategory.MEAL, 100).isPresent());
    }

    @Test
    void rareActiveEffectRequiresExplicitReplacementConfirmation() {
        ConsumableEffectState rare =
                success(
                        engine.apply(
                                ConsumableEffectState.empty(),
                                effect("effect.tonic.rare", ConsumableCategory.BODY_TONIC, true),
                                100,
                                false));

        Result<ConsumableEffectState, ConsumableEffectErrorCode> rejected =
                engine.apply(
                        rare,
                        effect("effect.tonic.common", ConsumableCategory.BODY_TONIC, false),
                        100,
                        false);

        assertFalse(rejected.isSuccess());
        assertEquals(
                ConsumableEffectErrorCode.RARE_REPLACEMENT_CONFIRMATION_REQUIRED,
                ((Result.Failure<ConsumableEffectState, ConsumableEffectErrorCode>) rejected)
                        .error());
        assertTrue(
                engine.apply(
                                rare,
                                effect("effect.tonic.common", ConsumableCategory.BODY_TONIC, false),
                                100,
                                true)
                        .isSuccess());
    }

    private static ActiveConsumableEffect effect(
            String id, ConsumableCategory category, boolean rare) {
        return new ActiveConsumableEffect(DefinitionId.of(id), category, 500, rare);
    }

    private static ConsumableEffectState success(
            Result<ConsumableEffectState, ConsumableEffectErrorCode> result) {
        assertTrue(result.isSuccess());
        return ((Result.Success<ConsumableEffectState, ConsumableEffectErrorCode>) result).value();
    }
}
