package com.branz.mmorpg.items.consumable;

import com.branz.mmorpg.api.result.Result;
import java.util.EnumMap;
import java.util.Objects;

/** Enforces one active consumable effect per category. */
public final class ConsumableEffectEngine {
    public Result<ConsumableEffectState, ConsumableEffectErrorCode> apply(
            ConsumableEffectState state,
            ActiveConsumableEffect incoming,
            long currentTick,
            boolean replacementConfirmed) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(incoming, "incoming");
        ActiveConsumableEffect existing =
                state.effect(incoming.category(), currentTick).orElse(null);
        if (existing != null && existing.rare() && !replacementConfirmed) {
            return Result.failure(
                    ConsumableEffectErrorCode.RARE_REPLACEMENT_CONFIRMATION_REQUIRED,
                    "Replacing " + existing.effectId().value() + " requires confirmation.");
        }
        EnumMap<ConsumableCategory, ActiveConsumableEffect> next =
                new EnumMap<>(ConsumableCategory.class);
        state.active().values().stream()
                .filter(effect -> effect.activeAt(currentTick))
                .forEach(effect -> next.put(effect.category(), effect));
        next.put(incoming.category(), incoming);
        return Result.success(new ConsumableEffectState(next));
    }
}
