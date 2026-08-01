package com.branz.mmorpg.items.consumable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ConsumableEffectState(Map<ConsumableCategory, ActiveConsumableEffect> active) {
    public ConsumableEffectState {
        Objects.requireNonNull(active, "active");
        EnumMap<ConsumableCategory, ActiveConsumableEffect> copy =
                new EnumMap<>(ConsumableCategory.class);
        active.forEach(
                (category, effect) -> {
                    if (category != Objects.requireNonNull(effect, "effect").category()) {
                        throw new IllegalArgumentException("effect category key mismatch");
                    }
                    copy.put(Objects.requireNonNull(category, "category"), effect);
                });
        active = Map.copyOf(copy);
    }

    public static ConsumableEffectState empty() {
        return new ConsumableEffectState(Map.of());
    }

    public Optional<ActiveConsumableEffect> effect(ConsumableCategory category, long currentTick) {
        ActiveConsumableEffect effect = active.get(Objects.requireNonNull(category, "category"));
        return effect != null && effect.activeAt(currentTick)
                ? Optional.of(effect)
                : Optional.empty();
    }
}
