package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.combat.resource.FlaskAllocation;
import com.branz.mmorpg.combat.resource.FlaskState;
import com.branz.mmorpg.combat.resource.PreparedFlaskSnapshot;
import com.branz.mmorpg.combat.status.AilmentType;
import com.branz.mmorpg.items.consumable.ConsumableCategory;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Restart-safe relative expedition state carried by the authoritative Player Session. */
record PersistentExpeditionState(
        FlaskState flaskState,
        List<PersistentConsumableEffect> consumableEffects,
        Map<AilmentType, PersistentAilmentState> ailments,
        Optional<PreparedFlaskSnapshot> preparedFlaskSnapshot) {
    PersistentExpeditionState(
            FlaskState flaskState,
            List<PersistentConsumableEffect> consumableEffects,
            Map<AilmentType, PersistentAilmentState> ailments) {
        this(flaskState, consumableEffects, ailments, Optional.empty());
    }

    PersistentExpeditionState {
        Objects.requireNonNull(flaskState, "flaskState");
        consumableEffects =
                List.copyOf(Objects.requireNonNull(consumableEffects, "consumableEffects"));
        EnumMap<ConsumableCategory, PersistentConsumableEffect> effects =
                new EnumMap<>(ConsumableCategory.class);
        for (PersistentConsumableEffect effect : consumableEffects) {
            if (effects.put(effect.category(), effect) != null) {
                throw new IllegalArgumentException("duplicate consumable effect category");
            }
        }
        Objects.requireNonNull(ailments, "ailments");
        EnumMap<AilmentType, PersistentAilmentState> copy = new EnumMap<>(AilmentType.class);
        ailments.forEach(
                (type, state) -> {
                    if (type != Objects.requireNonNull(state, "ailment state").type()) {
                        throw new IllegalArgumentException("ailment state key mismatch");
                    }
                    copy.put(Objects.requireNonNull(type, "ailment type"), state);
                });
        ailments = Map.copyOf(copy);
        Objects.requireNonNull(preparedFlaskSnapshot, "preparedFlaskSnapshot");
    }

    static PersistentExpeditionState initial() {
        return new PersistentExpeditionState(
                FlaskState.empty(FlaskAllocation.balanced()),
                List.of(),
                Map.of(),
                Optional.empty());
    }
}
